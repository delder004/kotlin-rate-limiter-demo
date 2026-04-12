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
    val apiTarget: String = "catfact",
    val overflowMode: String = "queue", // "queue" or "reject"
)

@Serializable
data class MetricPoint(
    val type: String = "metric",
    val timeMs: Long,
    val queued: Int,
    val completed: Int,
    val denied: Int,
    val avgLatencyMs: Long,
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

private suspend fun handleSimulationSocket(session: DefaultWebSocketServerSession) {
    var simulationJob: Job? = null

    try {
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val msg = json.decodeFromString<ControlMessage>(frame.readText())
                when (msg.action) {
                    "start" -> {
                        simulationJob?.cancelAndJoin()
                        simulationJob = CoroutineScope(Dispatchers.Default).launch {
                            runSimulation(session, msg.config!!, msg.requestsPerSecond, msg.apiTarget, msg.overflowMode)
                        }
                    }
                    "stop" -> {
                        simulationJob?.cancelAndJoin()
                        simulationJob = null
                    }
                    "updateRate" -> {
                        simulationJob?.cancelAndJoin()
                        simulationJob = CoroutineScope(Dispatchers.Default).launch {
                            runSimulation(session, msg.config!!, msg.requestsPerSecond, msg.apiTarget, msg.overflowMode)
                        }
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
    config: LimiterConfig,
    requestsPerSecond: Double,
    apiTarget: String,
    overflowMode: String = "queue",
) {
    val limiter = createLimiter(config)
    val targetUrl = apiTargets[apiTarget] ?: ""
    val reject = overflowMode == "reject"
    val mark = TimeSource.Monotonic.markNow()

    var queued = 0
    var completed = 0
    var denied = 0
    val latencies = mutableListOf<Long>()

    val requestChannel = Channel<Unit>(Channel.UNLIMITED)
    // Single outbound channel to avoid concurrent session.send()
    val outChannel = Channel<String>(Channel.UNLIMITED)

    // Sender: single coroutine that owns session.send()
    val sender = CoroutineScope(currentCoroutineContext()).launch {
        for (msg in outChannel) {
            session.send(Frame.Text(msg))
        }
    }

    // Producer: generates requests at the desired rate, checks permits before enqueuing
    val producer = CoroutineScope(currentCoroutineContext()).launch {
        val tickMs = 10L
        val requestsPerTick = requestsPerSecond * tickMs / 1000.0
        var accumulator = 0.0
        while (isActive) {
            accumulator += requestsPerTick
            val toEmit = accumulator.toInt()
            accumulator -= toEmit
            repeat(toEmit) {
                if (reject) {
                    when (val permit = limiter.tryAcquire()) {
                        is Permit.Granted -> {
                            requestChannel.send(Unit)
                            queued++
                        }
                        is Permit.Denied -> {
                            denied++
                            outChannel.send(json.encodeToString(ResponseLog(
                                timeMs = mark.elapsedNow().inWholeMilliseconds,
                                status = 429,
                                latencyMs = 0,
                                body = """{"error":"rate limited","retry_after":"${permit.retryAfter}"}""",
                            )))
                        }
                    }
                } else {
                    limiter.acquire()
                    requestChannel.send(Unit)
                    queued++
                }
            }
            delay(tickMs)
        }
    }

    // Workers: execute the actual HTTP call for permitted requests
    val workers = CoroutineScope(currentCoroutineContext()).launch {
        repeat(50) {
            launch {
                for (req in requestChannel) {
                    val reqStart = mark.elapsedNow()
                    val result = doWork(targetUrl)
                    completed++
                    val latency = (mark.elapsedNow() - reqStart).inWholeMilliseconds
                    synchronized(latencies) { latencies.add(latency) }
                    outChannel.send(json.encodeToString(ResponseLog(
                        timeMs = mark.elapsedNow().inWholeMilliseconds,
                        status = result.status,
                        latencyMs = latency,
                        body = result.body,
                    )))
                    queued--
                }
            }
        }
    }

    // Reporter: sends metrics every 200ms
    try {
        while (currentCoroutineContext().isActive) {
            delay(200)
            val avgLatency = synchronized(latencies) {
                if (latencies.isEmpty()) 0L
                else latencies.average().toLong().also { latencies.clear() }
            }
            val point = MetricPoint(
                timeMs = mark.elapsedNow().inWholeMilliseconds,
                queued = queued.coerceAtLeast(0),
                completed = completed,
                denied = denied,
                avgLatencyMs = avgLatency,
            )
            outChannel.send(json.encodeToString(point))
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

private suspend fun doWork(targetUrl: String): WorkResult {
    return if (targetUrl.isNotEmpty()) {
        try {
            val response = httpClient.get(targetUrl)
            val body = response.bodyAsText()
            WorkResult(response.status.value, body.take(200))
        } catch (e: Exception) {
            WorkResult(0, "error: ${e.message?.take(100)}")
        }
    } else {
        delay(10)
        WorkResult(200, "{\"simulated\": true}")
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
