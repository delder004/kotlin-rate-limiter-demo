package com.example.simulation

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationEngineTest {
    private fun baseConfig(
        overflowMode: OverflowMode = OverflowMode.QUEUE,
        requestsPerSecond: Double = 100.0,
        workerConcurrency: Int = 4,
        serviceTimeMs: Long = 10,
        jitterMs: Long = 0,
        failureRate: Double = 0.0,
    ) = SimulationConfig(
        limiterType = LimiterType.BURSTY,
        permits = 1000,
        perSeconds = 1.0,
        warmupSeconds = 0.0,
        requestsPerSecond = requestsPerSecond,
        overflowMode = overflowMode,
        apiTarget = ApiTarget.NONE,
        serviceTimeMs = serviceTimeMs,
        jitterMs = jitterMs,
        failureRate = failureRate,
        workerConcurrency = workerConcurrency,
    )

    private fun handle(
        config: SimulationConfig,
        logCapacity: Int = 200,
    ) = SimulationHandle(
        id = "test",
        initialConfig = config,
        createdAt = Instant.EPOCH,
        logBufferCapacity = logCapacity,
    )

    private class AlwaysGrantedLimiter(private val acquireDelayMs: Long = 0) : EngineLimiter {
        override suspend fun acquire() {
            if (acquireDelayMs > 0) delay(acquireDelayMs)
        }

        override fun tryAcquire(): EnginePermit = EnginePermit.Granted
    }

    private class AlwaysDeniedLimiter : EngineLimiter {
        override suspend fun acquire() {
            delay(Long.MAX_VALUE / 2)
        }

        override fun tryAcquire(): EnginePermit = EnginePermit.Denied(retryAfterMs = 1000)
    }

    @Test
    fun `reject mode increments denials under overload`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysDeniedLimiter() },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h = handle(baseConfig(overflowMode = OverflowMode.REJECT, requestsPerSecond = 1000.0))
            engine.start(h)
            advanceTimeBy(500)
            runCurrent()

            val metrics = h.currentMetrics
            assertTrue(metrics.denied > 0, "expected denied > 0, got ${metrics.denied}")
            assertEquals(0L, metrics.completed)
        }

    @Test
    fun `queue mode accumulates backlog when limiter blocks acquire`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysGrantedLimiter(acquireDelayMs = 100_000) },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h = handle(baseConfig(overflowMode = OverflowMode.QUEUE, requestsPerSecond = 200.0, workerConcurrency = 2))
            engine.start(h)
            advanceTimeBy(500)
            runCurrent()

            val metrics = h.currentMetrics
            assertTrue(metrics.queued > 0, "expected backlog, got queued=${metrics.queued}")
            assertEquals(0L, metrics.completed, "workers should still be blocked on acquire")
        }

    @Test
    fun `queue mode processes requests when limiter grants immediately`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysGrantedLimiter() },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h =
                handle(
                    baseConfig(
                        overflowMode = OverflowMode.QUEUE,
                        requestsPerSecond = 50.0,
                        serviceTimeMs = 5,
                        workerConcurrency = 8,
                    ),
                )
            engine.start(h)
            advanceTimeBy(1000)
            runCurrent()

            val metrics = h.currentMetrics
            assertTrue(metrics.completed > 0, "expected completions, got ${metrics.completed}")
            assertTrue(metrics.admitted >= metrics.completed)
        }

    @Test
    fun `latency reflects limiter wait time`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysGrantedLimiter(acquireDelayMs = 50) },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h =
                handle(
                    baseConfig(
                        overflowMode = OverflowMode.QUEUE,
                        requestsPerSecond = 20.0,
                        serviceTimeMs = 10,
                        workerConcurrency = 2,
                    ),
                )
            engine.start(h)
            advanceTimeBy(2000)
            runCurrent()

            val metrics = h.currentMetrics
            assertTrue(metrics.completed > 0, "expected completions")
            assertTrue(
                metrics.avgLatencyMs >= 50,
                "avg latency should include ~50ms acquire wait, got ${metrics.avgLatencyMs}",
            )
        }

    @Test
    fun `stopping engineJob halts further completions`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysGrantedLimiter() },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h = handle(baseConfig(requestsPerSecond = 50.0, serviceTimeMs = 5, workerConcurrency = 4))
            engine.start(h)
            advanceTimeBy(500)
            runCurrent()

            val midCompletions = h.currentMetrics.completed
            assertTrue(midCompletions > 0)

            h.engineJob?.cancel()
            advanceTimeBy(500)
            runCurrent()

            val lastCompletions = h.currentMetrics.completed
            advanceTimeBy(1000)
            runCurrent()
            val stillLastCompletions = h.currentMetrics.completed
            assertEquals(
                lastCompletions,
                stillLastCompletions,
                "completions should not grow after cancel",
            )
        }

    @Test
    fun `bounded log buffer retains capacity and tracks drops`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysGrantedLimiter() },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h =
                handle(
                    config = baseConfig(requestsPerSecond = 200.0, serviceTimeMs = 1, workerConcurrency = 8),
                    logCapacity = 20,
                )
            engine.start(h)
            advanceTimeBy(1000)
            runCurrent()

            assertTrue(h.recentLogs.size <= 20)
            assertTrue(h.droppedLogCount > 0, "expected log drops, got ${h.droppedLogCount}")
        }

    @Test
    fun `droppedIncoming counter is present in snapshot`() =
        runTest {
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { AlwaysGrantedLimiter() },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val h = handle(baseConfig(requestsPerSecond = 10.0, serviceTimeMs = 5, workerConcurrency = 2))
            engine.start(h)
            advanceTimeBy(500)
            runCurrent()

            val snapshot = h.currentMetrics
            assertEquals(0L, snapshot.droppedOutgoing)
            assertEquals(0L, snapshot.droppedIncoming)
        }
}
