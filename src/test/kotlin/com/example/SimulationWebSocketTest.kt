package com.example

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration coverage for the WebSocket simulation path: runSimulation,
 * ControlMessage round-tripping, and the metric stream emitted over the socket.
 *
 * These tests use Ktor's testApplication to mount the real handleSimulationSocket
 * handler, drive it with a real WebSocket client, and collect MetricPoint frames.
 * The simulated upstream (apiTarget = "none") means no network — all delays are
 * controlled by serviceTimeMs. Each test takes ~2s of wall time.
 */
class SimulationWebSocketTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `ControlMessage round-trips through JSON with all fields preserved`() {
        val msg = ControlMessage(
            action = "start",
            config = LimiterConfig(
                type = "composite",
                permits = 10,
                perSeconds = 1.0,
                warmupSeconds = 2.5,
                secondaryPermits = 30,
                secondaryPerSeconds = 60.0,
            ),
            requestsPerSecond = 123.5,
            apiTarget = "none",
            overflowMode = "reject",
            serviceTimeMs = 100,
            jitterMs = 25,
            failureRate = 0.1,
            workerConcurrency = 25,
        )
        val encoded = json.encodeToString(msg)
        val decoded = json.decodeFromString<ControlMessage>(encoded)
        assertEquals(msg, decoded)
    }

    @Test
    fun `MetricPoint round-trips through JSON`() {
        val point = MetricPoint(
            timeMs = 1234,
            queued = 50,
            admitted = 10,
            completed = 8,
            denied = 20,
            inFlight = 2,
            avgLatencyMs = 150,
            p50LatencyMs = 120,
            p95LatencyMs = 500,
            acceptRate = 5.0,
            rejectRate = 10.0,
        )
        val encoded = json.encodeToString(point)
        val decoded = json.decodeFromString<MetricPoint>(encoded)
        assertEquals(point, decoded)
    }

    @Test
    fun `queue mode overload emits backlog and end-to-end latency`() = testApplication {
        application {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                webSocket("/ws") { handleSimulationSocket(this) }
            }
        }
        val client = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }

        val metrics = client.collectMetrics(
            start = ControlMessage(
                action = "start",
                config = LimiterConfig(type = "bursty", permits = 5, perSeconds = 1.0),
                requestsPerSecond = 50.0,
                apiTarget = "none",
                overflowMode = "queue",
                serviceTimeMs = 50,
                jitterMs = 0,
                failureRate = 0.0,
                workerConcurrency = 50,
            ),
            collectMs = 2200,
        )

        assertTrue(metrics.size >= 5, "Should collect several metric points, got ${metrics.size}")

        // FIX 1: Latency is end-to-end — under overload, p95 must exceed service time.
        val maxP95 = metrics.maxOf { it.p95LatencyMs }
        assertTrue(
            maxP95 > 200,
            "p95 latency should include queue + permit wait, got max=${maxP95}ms (service time is 50ms)",
        )

        // FIX 3: Queue depth stays visible (not hidden inside worker-blocked state).
        val maxQueued = metrics.maxOf { it.queued }
        assertTrue(
            maxQueued >= 10,
            "Queue should be visible under 50 req/s → 5/s limit, got max=$maxQueued",
        )

        // acceptRate should track the limiter capacity (5/s).
        val steadyAcceptRates = metrics.drop(3).map { it.acceptRate }.filter { it > 0 }
        if (steadyAcceptRates.isNotEmpty()) {
            val avg = steadyAcceptRates.average()
            assertTrue(
                avg in 2.0..9.0,
                "Steady-state acceptRate should be near 5/s (the limiter capacity), got avg=$avg",
            )
        }
    }

    @Test
    fun `reject mode separates admission rate from completion rate`() = testApplication {
        application {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                webSocket("/ws") { handleSimulationSocket(this) }
            }
        }
        val client = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }

        // Slow service (400ms) means completions lag behind admissions. Accept/s
        // must still reflect the limiter capacity, not the throughput.
        val metrics = client.collectMetrics(
            start = ControlMessage(
                action = "start",
                config = LimiterConfig(type = "bursty", permits = 10, perSeconds = 1.0),
                requestsPerSecond = 100.0,
                apiTarget = "none",
                overflowMode = "reject",
                serviceTimeMs = 400,
                jitterMs = 0,
                failureRate = 0.0,
                workerConcurrency = 50,
            ),
            collectMs = 2200,
        )

        assertTrue(metrics.size >= 5, "Should collect several metric points, got ${metrics.size}")

        val last = metrics.last()
        // FIX 2: admissions are independent of completions. With a 400ms service
        // time, admitted should noticeably exceed completed while the in-flight
        // requests are still being processed.
        assertTrue(
            last.admitted > last.completed,
            "admitted (${last.admitted}) should exceed completed (${last.completed}) while work is still in flight",
        )

        // Over the run, acceptRate should average near the 10/s limiter capacity.
        val steadyAcceptRates = metrics.drop(2).map { it.acceptRate }.filter { it > 0 }
        assertTrue(steadyAcceptRates.isNotEmpty(), "Should see non-zero acceptRate samples")
        val avgAccept = steadyAcceptRates.average()
        assertTrue(
            avgAccept in 5.0..15.0,
            "Steady-state acceptRate should be ~10/s (limiter capacity), got avg=$avgAccept",
        )

        // Overload must produce denials.
        assertTrue(last.denied > 0, "Reject mode with overload should produce denials")
        val steadyRejectRates = metrics.drop(2).map { it.rejectRate }.filter { it > 0 }
        assertTrue(steadyRejectRates.isNotEmpty(), "Should see non-zero rejectRate samples")
    }

    @Test
    fun `conservation of requests — admitted equals completed plus inFlight plus post-admission queue`() = testApplication {
        application {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                webSocket("/ws") { handleSimulationSocket(this) }
            }
        }
        val client = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }

        // Reject mode: every admitted request is either in the channel (queued),
        // running (inFlight), or finished (completed). No request escapes counting.
        val metrics = client.collectMetrics(
            start = ControlMessage(
                action = "start",
                config = LimiterConfig(type = "bursty", permits = 10, perSeconds = 1.0),
                requestsPerSecond = 30.0,
                apiTarget = "none",
                overflowMode = "reject",
                serviceTimeMs = 200,
                jitterMs = 0,
                failureRate = 0.0,
                workerConcurrency = 20,
            ),
            collectMs = 2200,
        )

        assertTrue(metrics.size >= 5)
        // Sampling is not atomic across counters, so allow a small slack.
        metrics.forEach { m ->
            val accounted = m.completed + m.inFlight + m.queued
            val slack = 5
            assertTrue(
                accounted in (m.admitted - slack)..(m.admitted + slack),
                "admitted=${m.admitted} should ≈ completed(${m.completed}) + inFlight(${m.inFlight}) + queued(${m.queued}) (got accounted=$accounted)",
            )
        }
    }

    // === Helpers ===

    private suspend fun io.ktor.client.HttpClient.collectMetrics(
        start: ControlMessage,
        collectMs: Long,
    ): List<MetricPoint> {
        val collected = mutableListOf<MetricPoint>()
        webSocket("/ws") {
            send(Frame.Text(json.encodeToString(start)))
            val deadline = System.currentTimeMillis() + collectMs
            while (System.currentTimeMillis() < deadline) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                val frame = withTimeoutOrNull(remaining) { incoming.receive() } ?: break
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val element = json.parseToJsonElement(text).jsonObject
                    if (element["type"]?.jsonPrimitive?.content == "metric") {
                        collected.add(json.decodeFromString<MetricPoint>(text))
                    }
                }
            }
            send(Frame.Text(json.encodeToString(ControlMessage(action = "stop"))))
        }
        return collected
    }
}
