package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ratelimiter.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val httpClient = HttpClient(CIO) {
    engine {
        maxConnectionsCount = 50
        endpoint {
            maxConnectionsPerRoute = 50
            keepAliveTime = 10_000
            connectTimeout = 5_000
            pipelineMaxSize = 20
        }
    }
}

private val apiTargets = mapOf(
    "catfact" to "https://catfact.ninja/fact",
    "jsonplaceholder" to "https://jsonplaceholder.typicode.com/posts/1",
    "none" to "",
)

@Serializable
data class LimiterConfig(
    val type: String, // "bursty", "smooth", "composite"
    val permits: Int,
    val perSeconds: Double,
    val warmupSeconds: Double = 0.0,
    // Composite secondary limiter
    val secondaryPermits: Int = 0,
    val secondaryPerSeconds: Double = 0.0,
)

@Serializable
data class ControlMessage(
    val action: String, // "start", "stop", "updateRate"
    val config: LimiterConfig? = null,
    val requestsPerSecond: Double = 1.0,
    val apiTarget: String = "none",
    val overflowMode: String = "queue", // "queue" or "reject"
    val serviceTimeMs: Long = 50,
    val jitterMs: Long = 20,
    val failureRate: Double = 0.0,
    val workerConcurrency: Int = 50,
)

@Serializable
data class MetricPoint(
    val type: String = "metric",
    val timeMs: Long,
    val queued: Int,
    val admitted: Int,
    val completed: Int,
    val denied: Int,
    val inFlight: Int,
    val avgLatencyMs: Long,
    val p50LatencyMs: Long,
    val p95LatencyMs: Long,
    val acceptRate: Double,
    val rejectRate: Double,
)

@Serializable
data class ResponseLog(
    val type: String = "response",
    val timeMs: Long,
    val status: Int,
    val latencyMs: Long,
    val body: String,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun main() {
    embeddedServer(Netty, port = 8080) {
        install(WebSockets)
        routing {
            get("/") {
                call.respondHtml { dashboardPage() }
            }
            webSocket("/ws") {
                handleSimulationSocket(this)
            }
        }
    }.start(wait = true)
}

internal suspend fun handleSimulationSocket(session: DefaultWebSocketServerSession) {
    var simulationJob: Job? = null

    try {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val msg = json.decodeFromString<ControlMessage>(frame.readText())
                when (msg.action) {
                    "start", "updateRate" -> {
                        simulationJob?.cancelAndJoin()
                        simulationJob = CoroutineScope(Dispatchers.Default).launch {
                            runSimulation(session, msg)
                        }
                    }
                    "stop" -> {
                        simulationJob?.cancelAndJoin()
                        simulationJob = null
                    }
                }
            }
        }
    } finally {
        simulationJob?.cancelAndJoin()
    }
}

private suspend fun runSimulation(
    session: DefaultWebSocketServerSession,
    msg: ControlMessage,
) {
    val config = msg.config!!
    val limiter = createLimiter(config)
    val targetUrl = apiTargets[msg.apiTarget] ?: ""
    val reject = msg.overflowMode == "reject"
    val mark = TimeSource.Monotonic.markNow()

    // === Metric state (all lock-free) ===
    // queued: arrived but not yet admitted (sitting in channel OR worker blocked on acquire())
    // admitted: total admission events (rate limiter granted a permit); drives accept/s
    // inFlight: currently executing doWork() (post-admission, pre-completion)
    // completed: total doWork() finishes; drives throughput
    val queued = AtomicInteger(0)
    val admitted = AtomicInteger(0)
    val completed = AtomicInteger(0)
    val denied = AtomicInteger(0)
    val inFlight = AtomicInteger(0)
    val latencies = ConcurrentLinkedQueue<Long>()

    // Channel payload: arrival time (ms since mark), so latency is measured
    // end-to-end from arrival → completion, including queue wait and permit wait.
    val requestChannel = Channel<Long>(capacity = 1000)
    // Bounded outbound channel: avoids runaway memory if dashboard can't keep up.
    val outChannel = Channel<String>(capacity = 5000)

    // Sample response logs so high arrival rates don't flood the websocket.
    val logSampleEvery = maxOf(1, (msg.requestsPerSecond / 50.0).toInt())
    val logSampleCounter = AtomicInteger(0)

    suspend fun emitLog(log: ResponseLog, force: Boolean = false) {
        if (force || logSampleCounter.incrementAndGet() % logSampleEvery == 0) {
            // trySend avoids blocking the worker path under pressure
            outChannel.trySend(json.encodeToString(log))
        }
    }

    // === Sender: single coroutine owns session.send() ===
    val sender = CoroutineScope(currentCoroutineContext()).launch {
        for (m in outChannel) {
            session.send(Frame.Text(m))
        }
    }

    // === Producer: generates requests at the configured arrival rate. ===
    // In reject mode, the producer is also the admission controller: it calls
    // tryAcquire() and either enqueues the work or emits a 429 log.
    // In queue mode, the producer enqueues unconditionally — the rate limit is
    // applied inside the worker, which is the correct "server-side backlog" model.
    val producer = CoroutineScope(currentCoroutineContext()).launch {
        val tickMs = 10L
        val requestsPerTick = msg.requestsPerSecond * tickMs / 1000.0
        var accumulator = 0.0
        while (isActive) {
            accumulator += requestsPerTick
            val toEmit = accumulator.toInt()
            accumulator -= toEmit
            repeat(toEmit) {
                val arrivalMs = mark.elapsedNow().inWholeMilliseconds
                if (reject) {
                    when (val permit = limiter.tryAcquire()) {
                        is Permit.Granted -> {
                            // Admission happens here in reject mode.
                            admitted.incrementAndGet()
                            queued.incrementAndGet()
                            requestChannel.send(arrivalMs)
                        }
                        is Permit.Denied -> {
                            denied.incrementAndGet()
                            emitLog(
                                ResponseLog(
                                    timeMs = arrivalMs,
                                    status = 429,
                                    latencyMs = 0,
                                    body = """{"error":"rate limited","retry_after":"${permit.retryAfter}"}""",
                                ),
                            )
                        }
                    }
                } else {
                    // Queue mode: requests arrive unconditionally; limiter gates workers.
                    queued.incrementAndGet()
                    requestChannel.send(arrivalMs)
                }
            }
            delay(tickMs)
        }
    }

    // === Workers: drain the queue and execute work. ===
    // In queue mode, each worker calls limiter.acquire() before doWork() — this
    // is what creates the realistic server-side backlog when arrival > capacity.
    // Requests stay counted as "queued" until they are actually admitted, so a
    // worker blocked on acquire() is still visible as backlog in the dashboard.
    val workers = CoroutineScope(currentCoroutineContext()).launch {
        repeat(msg.workerConcurrency) {
            launch {
                for (arrivalMs in requestChannel) {
                    if (!reject) {
                        // Admission happens here in queue mode. While acquire()
                        // suspends, the request still counts as queued.
                        limiter.acquire()
                        admitted.incrementAndGet()
                    }
                    queued.decrementAndGet()
                    inFlight.incrementAndGet()
                    val result = doWork(targetUrl, msg.serviceTimeMs, msg.jitterMs, msg.failureRate)
                    val completionMs = mark.elapsedNow().inWholeMilliseconds
                    // End-to-end latency: from arrival, through queue wait and
                    // permit wait, through service time, to completion.
                    val latency = completionMs - arrivalMs
                    latencies.add(latency)
                    completed.incrementAndGet()
                    inFlight.decrementAndGet()
                    emitLog(
                        ResponseLog(
                            timeMs = completionMs,
                            status = result.status,
                            latencyMs = latency,
                            body = result.body,
                        ),
                    )
                }
            }
        }
    }

    // === Reporter: sends metrics every 200ms. ===
    var lastAdmitted = 0
    var lastDenied = 0
    try {
        while (currentCoroutineContext().isActive) {
            delay(200)

            // Drain the lock-free latency queue into a snapshot.
            val snapshot = ArrayList<Long>()
            while (true) {
                val v = latencies.poll() ?: break
                snapshot.add(v)
            }
            snapshot.sort()
            val avg = if (snapshot.isEmpty()) 0L else snapshot.average().toLong()
            val p50 = if (snapshot.isEmpty()) 0L else snapshot[snapshot.size / 2]
            val p95 = if (snapshot.isEmpty()) 0L else {
                val idx = ((snapshot.size - 1) * 0.95).toInt().coerceAtMost(snapshot.size - 1)
                snapshot[idx]
            }

            // Accept rate = admission delta (permits granted), NOT completion delta.
            // In reject mode under slow service, admissions can equal the limiter
            // capacity while completions lag — the chart should reflect admission.
            val admittedNow = admitted.get()
            val deniedNow = denied.get()
            val acceptRate = (admittedNow - lastAdmitted) / 0.2
            val rejectRate = (deniedNow - lastDenied) / 0.2
            lastAdmitted = admittedNow
            lastDenied = deniedNow

            val point = MetricPoint(
                timeMs = mark.elapsedNow().inWholeMilliseconds,
                queued = queued.get().coerceAtLeast(0),
                admitted = admittedNow,
                completed = completed.get(),
                denied = deniedNow,
                inFlight = inFlight.get().coerceAtLeast(0),
                avgLatencyMs = avg,
                p50LatencyMs = p50,
                p95LatencyMs = p95,
                acceptRate = acceptRate,
                rejectRate = rejectRate,
            )
            outChannel.trySend(json.encodeToString(point))
        }
    } finally {
        producer.cancel()
        workers.cancel()
        requestChannel.close()
        outChannel.close()
        sender.cancel()
    }
}

private data class WorkResult(val status: Int, val body: String)

private suspend fun doWork(
    targetUrl: String,
    serviceTimeMs: Long,
    jitterMs: Long,
    failureRate: Double,
): WorkResult {
    return if (targetUrl.isNotEmpty()) {
        try {
            val response = httpClient.get(targetUrl)
            val body = response.bodyAsText()
            WorkResult(response.status.value, body.take(200))
        } catch (e: Exception) {
            WorkResult(0, "error: ${e.message?.take(100)}")
        }
    } else {
        val jitter = if (jitterMs > 0) Random.nextLong(jitterMs + 1) else 0L
        delay(serviceTimeMs + jitter)
        if (failureRate > 0.0 && Random.nextDouble() < failureRate) {
            WorkResult(500, """{"error":"simulated failure"}""")
        } else {
            WorkResult(200, """{"simulated":true,"serviceTimeMs":${serviceTimeMs + jitter}}""")
        }
    }
}

private fun createLimiter(config: LimiterConfig): RateLimiter {
    val perDuration = (config.perSeconds * 1000).toLong().milliseconds
    return when (config.type) {
        "smooth" -> SmoothRateLimiter(
            permits = config.permits,
            per = perDuration,
            warmup = (config.warmupSeconds * 1000).toLong().milliseconds,
        )
        "composite" -> {
            val primary = BurstyRateLimiter(permits = config.permits, per = perDuration)
            val secondaryPer = (config.secondaryPerSeconds * 1000).toLong().milliseconds
            val secondary = BurstyRateLimiter(permits = config.secondaryPermits, per = secondaryPer)
            CompositeRateLimiter(primary, secondary)
        }
        else -> BurstyRateLimiter(permits = config.permits, per = perDuration)
    }
}
