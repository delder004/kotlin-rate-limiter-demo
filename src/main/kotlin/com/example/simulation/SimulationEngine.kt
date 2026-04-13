package com.example.simulation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

interface SimulationEngine {
    fun start(handle: SimulationHandle)
}

object NoopSimulationEngine : SimulationEngine {
    override fun start(handle: SimulationHandle) {
        // no-op: lifecycle exists without real runtime work
    }
}

class CoroutineSimulationEngine(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val limiterFactory: (SimulationConfig) -> EngineLimiter = LimiterFactory::create,
    private val timeSource: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val tickMs: Long = 20,
    private val metricsIntervalMs: Long = 100,
    private val requestQueueCapacity: Int = 1000,
) : SimulationEngine {
    override fun start(handle: SimulationHandle) {
        val job = scope.launch { runLoop(handle) }
        handle.engineJob = job
    }

    private suspend fun runLoop(handle: SimulationHandle) {
        try {
            coroutineScope { runInnerLoop(handle) }
        } catch (_: CancellationException) {
            // clean stop
        }
    }

    private suspend fun runInnerLoop(handle: SimulationHandle) =
        coroutineScope {
            val initialConfig = handle.config
            handle.limiterRef.set(limiterFactory(initialConfig))
            val limiter = LiveEngineLimiter(handle.limiterRef)
            val startMs = timeSource()

            val queued = AtomicInteger(0)
            val admitted = AtomicLong(0)
            val completed = AtomicLong(0)
            val denied = AtomicLong(0)
            val droppedIncoming = AtomicLong(0)
            val inFlight = AtomicInteger(0)
            val latencies = ConcurrentLinkedQueue<Long>()

            val requestChannel = Channel<Long>(capacity = requestQueueCapacity)

            val producer = launch { runProducer(limiter, startMs, requestChannel, queued, admitted, denied, droppedIncoming, handle) }
            val workerJobs =
                List(initialConfig.workerConcurrency) {
                    launch { runWorker(limiter, startMs, requestChannel, queued, admitted, completed, inFlight, latencies, handle) }
                }
            val reporter =
                launch { runReporter(handle, startMs, queued, admitted, completed, denied, droppedIncoming, inFlight, latencies) }

            try {
                producer.join()
            } finally {
                producer.cancel()
                workerJobs.forEach { it.cancel() }
                reporter.cancel()
                requestChannel.close()
            }
        }

    private suspend fun runProducer(
        limiter: EngineLimiter,
        startMs: Long,
        requestChannel: Channel<Long>,
        queued: AtomicInteger,
        admitted: AtomicLong,
        denied: AtomicLong,
        droppedIncoming: AtomicLong,
        handle: SimulationHandle,
    ) {
        var accumulator = 0.0
        while (currentScopeActive()) {
            val liveConfig = handle.config
            val reject = liveConfig.overflowMode == OverflowMode.REJECT
            val requestsPerTick = liveConfig.requestsPerSecond * tickMs / 1000.0
            accumulator += requestsPerTick
            val toEmit = accumulator.toInt()
            accumulator -= toEmit
            repeat(toEmit) {
                val arrivalMs = timeSource() - startMs
                if (reject) {
                    when (limiter.tryAcquire()) {
                        is EnginePermit.Granted -> {
                            admitted.incrementAndGet()
                            queued.incrementAndGet()
                            val result = requestChannel.trySend(arrivalMs)
                            if (!result.isSuccess) {
                                queued.decrementAndGet()
                                droppedIncoming.incrementAndGet()
                            }
                        }
                        is EnginePermit.Denied -> {
                            denied.incrementAndGet()
                            val entry =
                                LogEntry(
                                    timeMs = arrivalMs,
                                    status = 429,
                                    latencyMs = 0,
                                    body = "rate limited",
                                )
                            handle.appendLog(entry)
                            handle.publish(SimulationEvent.ResponseSample(handle.id, entry))
                        }
                    }
                } else {
                    queued.incrementAndGet()
                    val result = requestChannel.trySend(arrivalMs)
                    if (!result.isSuccess) {
                        queued.decrementAndGet()
                        droppedIncoming.incrementAndGet()
                    }
                }
            }
            delay(tickMs)
        }
    }

    private suspend fun runWorker(
        limiter: EngineLimiter,
        startMs: Long,
        requestChannel: Channel<Long>,
        queued: AtomicInteger,
        admitted: AtomicLong,
        completed: AtomicLong,
        inFlight: AtomicInteger,
        latencies: ConcurrentLinkedQueue<Long>,
        handle: SimulationHandle,
    ) {
        for (arrivalMs in requestChannel) {
            val liveConfigForAcquire = handle.config
            if (liveConfigForAcquire.overflowMode != OverflowMode.REJECT) {
                limiter.acquire()
                admitted.incrementAndGet()
            }
            queued.decrementAndGet()
            inFlight.incrementAndGet()
            val liveConfig = handle.config
            val jitter = if (liveConfig.jitterMs > 0) random.nextLong(liveConfig.jitterMs + 1) else 0L
            delay(liveConfig.serviceTimeMs + jitter)
            val failed = liveConfig.failureRate > 0.0 && random.nextDouble() < liveConfig.failureRate
            val completionMs = timeSource() - startMs
            val latency = completionMs - arrivalMs
            latencies.add(latency)
            completed.incrementAndGet()
            inFlight.decrementAndGet()
            val entry =
                LogEntry(
                    timeMs = completionMs,
                    status = if (failed) 500 else 200,
                    latencyMs = latency,
                    body = if (failed) "simulated failure" else "ok",
                )
            handle.appendLog(entry)
            handle.publish(SimulationEvent.ResponseSample(handle.id, entry))
        }
    }

    private suspend fun runReporter(
        handle: SimulationHandle,
        startMs: Long,
        queued: AtomicInteger,
        admitted: AtomicLong,
        completed: AtomicLong,
        denied: AtomicLong,
        droppedIncoming: AtomicLong,
        inFlight: AtomicInteger,
        latencies: ConcurrentLinkedQueue<Long>,
    ) {
        var lastAdmitted = admitted.get()
        var lastDenied = denied.get()
        while (currentScopeActive()) {
            delay(metricsIntervalMs)
            val buf = ArrayList<Long>()
            while (true) {
                val v = latencies.poll() ?: break
                buf.add(v)
            }
            buf.sort()
            val avg = if (buf.isEmpty()) 0L else buf.average().toLong()
            val p50 = if (buf.isEmpty()) 0L else buf[buf.size / 2]
            val p95 =
                if (buf.isEmpty()) {
                    0L
                } else {
                    val idx = ((buf.size - 1) * 0.95).toInt().coerceAtMost(buf.size - 1)
                    buf[idx]
                }
            val admittedNow = admitted.get()
            val deniedNow = denied.get()
            val dt = metricsIntervalMs / 1000.0
            val acceptRate = (admittedNow - lastAdmitted) / dt
            val rejectRate = (deniedNow - lastDenied) / dt
            lastAdmitted = admittedNow
            lastDenied = deniedNow

            val snapshot =
                MetricsSnapshot(
                    timeMs = timeSource() - startMs,
                    queued = queued.get().coerceAtLeast(0),
                    inFlight = inFlight.get().coerceAtLeast(0),
                    admitted = admittedNow,
                    completed = completed.get(),
                    denied = deniedNow,
                    droppedIncoming = droppedIncoming.get(),
                    droppedOutgoing = handle.droppedOutgoingCount,
                    acceptRate = acceptRate,
                    rejectRate = rejectRate,
                    avgLatencyMs = avg,
                    p50LatencyMs = p50,
                    p95LatencyMs = p95,
                )
            handle.updateMetrics(snapshot)
            handle.publish(SimulationEvent.MetricSample(handle.id, snapshot))
        }
    }

    private suspend fun currentScopeActive(): Boolean = kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive ?: true
}

private class LiveEngineLimiter(
    private val ref: java.util.concurrent.atomic.AtomicReference<EngineLimiter?>,
) : EngineLimiter {
    override suspend fun acquire() {
        ref.get()?.acquire()
    }

    override fun tryAcquire(): EnginePermit = ref.get()?.tryAcquire() ?: EnginePermit.Granted
}
