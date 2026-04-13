package com.example.web

import com.example.module
import com.example.simulation.ApiTarget
import com.example.simulation.LimiterType
import com.example.simulation.LogEntry
import com.example.simulation.MetricsSnapshot
import com.example.simulation.OverflowMode
import com.example.simulation.SimulationConfig
import com.example.simulation.SimulationEvent
import com.example.simulation.SimulationHandle
import com.example.simulation.SimulationRegistry
import com.example.simulation.SimulationStatus
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseStreamTest {
    private fun validConfig() =
        SimulationConfig(
            limiterType = LimiterType.BURSTY,
            permits = 5,
            perSeconds = 1.0,
            warmupSeconds = 0.0,
            requestsPerSecond = 0.0,
            overflowMode = OverflowMode.QUEUE,
            apiTarget = ApiTarget.NONE,
            serviceTimeMs = 0,
            jitterMs = 0,
            failureRate = 0.0,
            workerConcurrency = 1,
        )

    private suspend fun readInitial(
        response: io.ktor.client.statement.HttpResponse,
        minBytes: Int = 200,
        timeoutMs: Long = 2000,
    ): String {
        val chan = response.bodyAsChannel()
        val sb = StringBuilder()
        val buffer = ByteArray(4096)
        withTimeoutOrNull(timeoutMs) {
            while (sb.length < minBytes) {
                val n = chan.readAvailable(buffer, 0, buffer.size)
                if (n <= 0) break
                sb.append(String(buffer, 0, n))
            }
        }
        return sb.toString()
    }

    private suspend fun drain(
        response: io.ktor.client.statement.HttpResponse,
        timeoutMs: Long = 2000,
    ): String {
        val chan = response.bodyAsChannel()
        val sb = StringBuilder()
        val buffer = ByteArray(4096)
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val n = chan.readAvailable(buffer, 0, buffer.size)
                if (n <= 0) break
                sb.append(String(buffer, 0, n))
            }
        }
        return sb.toString()
    }

    @Test
    fun `stream returns 404 for unknown simulation`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val response = client.get("/simulations/no-such-id/stream")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `stream returns 404 for a simulation that was stopped and evicted`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val handle = registry.create(validConfig())
            registry.stop(handle.id)

            val response = client.get("/simulations/${handle.id}/stream")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `stream does not hang if stop wins the race between lookup and subscriber attach`() =
        testApplication {
            // Reproduces the GET-stop-attach interleaving: the handler has
            // already fetched the handle from the registry, but stop's
            // synchronized block runs before the handler attaches. Without
            // attachSubscriberIfRunning, the handler would add a fresh
            // subscriber after closeAllSubscribers already drained and block
            // forever waiting for events on a channel nobody will close.
            //
            // We model the race without real threads by leaving the handle in
            // the registry (so get() returns it) but flipping its status to
            // STOPPED (so the second check inside the handler fires).
            val registry = SimulationRegistry()
            application { module(registry) }
            val handle = registry.create(validConfig())
            handle.status = SimulationStatus.STOPPED

            val scope = CoroutineScope(coroutineContext)
            val streamDeferred =
                scope.async {
                    client.get("/simulations/${handle.id}/stream").bodyAsText()
                }

            withTimeoutOrNull(5_000) { streamDeferred.await() }
                ?: run {
                    streamDeferred.cancel()
                    error("stream handler hung on a stopped handle")
                }

            assertEquals(0, handle.subscriberCount, "no subscriber should have been attached")
        }

    @Test
    fun `stream initial state for running simulation is emitted before stop`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val handle = registry.create(validConfig())

            val scope = CoroutineScope(coroutineContext)
            val streamDeferred =
                scope.async {
                    client.get("/simulations/${handle.id}/stream").bodyAsText()
                }

            // Wait for subscriber to attach (server runs concurrently)
            withTimeoutOrNull(5_000) {
                while (handle.subscriberCount == 0) delay(10)
            }
            assertTrue(handle.subscriberCount >= 1, "subscriber never attached")

            // Stop to unblock the server event loop
            registry.stop(handle.id)
            val body = streamDeferred.await()

            assertTrue("\"id\":\"${handle.id}\"" in body)
            assertTrue("\"status\":\"running\"" in body, "initial state should reflect running; got:\n$body")
            assertTrue("\"status\":\"stopped\"" in body, "final state should reflect stopped; got:\n$body")
            assertEquals(0, handle.subscriberCount)
        }

    @Test
    fun `stream mapper produces metric signal patches`() {
        val snapshot =
            MetricsSnapshot.Empty.copy(
                timeMs = 100,
                queued = 3,
                inFlight = 2,
                admitted = 10,
                completed = 7,
                denied = 1,
                droppedIncoming = 0,
                droppedOutgoing = 0,
                acceptRate = 50.0,
                rejectRate = 5.0,
                avgLatencyMs = 12,
                p50LatencyMs = 10,
                p95LatencyMs = 20,
            )
        val events =
            StreamEventMapper.toDatastarEvents(
                SimulationEvent.MetricSample(simulationId = "sim-1", snapshot = snapshot),
            )
        assertEquals(1, events.size)
        val body = events.first().render()
        assertTrue("datastar-merge-signals" in body)
        assertTrue("\"queued\":3" in body)
        assertTrue("\"admitted\":10" in body)
        assertTrue("\"completed\":7" in body)
    }

    @Test
    fun `stream mapper produces log row fragment prepend patches`() {
        val entry = LogEntry(timeMs = 42, status = 200, latencyMs = 15, body = "ok")
        val events =
            StreamEventMapper.toDatastarEvents(
                SimulationEvent.ResponseSample(simulationId = "sim-1", entry = entry),
            )
        assertEquals(1, events.size)
        val body = events.first().render()
        assertTrue("datastar-merge-fragments" in body)
        assertTrue("selector #$LOG_LIST_ID" in body)
        assertTrue("mergeMode prepend" in body)
        assertTrue("status=200" in body)
    }

    @Test
    fun `stream mapper emits stopped lifecycle signal on Stopped event`() {
        val events = StreamEventMapper.toDatastarEvents(SimulationEvent.Stopped(simulationId = "sim-1"))
        assertEquals(1, events.size)
        val body = events.first().render()
        assertTrue("datastar-merge-signals" in body)
        assertTrue("\"status\":\"stopped\"" in body)
        assertTrue("\"running\":false" in body)
    }

    @Test
    fun `stream survives a valid update preserving sim id`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val handle = registry.create(validConfig())

            val scope = CoroutineScope(coroutineContext)
            val streamDeferred =
                scope.async {
                    client.get("/simulations/${handle.id}/stream").bodyAsText()
                }

            withTimeoutOrNull(5_000) {
                while (handle.subscriberCount == 0) delay(10)
            }
            assertTrue(handle.subscriberCount >= 1)

            // Update config with a new valid shape
            val newConfig = validConfig().copy(permits = 20, requestsPerSecond = 10.0)
            val updateResult = registry.update(handle.id, newConfig)
            assertTrue(updateResult is com.example.simulation.SimulationRegistry.UpdateResult.Updated)

            // Stop to drain the stream cleanly
            registry.stop(handle.id)
            val body = streamDeferred.await()

            assertTrue("\"id\":\"${handle.id}\"" in body, "id preserved")
            // Subscriber stayed attached through the update (no re-attach required);
            // registry eviction happens on stop, so we only assert the stream body.
        }

    @Test
    fun `handle publish increments droppedOutgoing when subscriber buffer is full`() {
        val handle =
            SimulationHandle(
                id = "t",
                initialConfig = validConfig(),
                createdAt = Instant.EPOCH,
            )
        val sub = handle.attachSubscriber(capacity = 2)
        repeat(5) { handle.publish(SimulationEvent.Stopped("t")) }
        assertTrue(
            handle.droppedOutgoingCount > 0,
            "expected drops, got ${handle.droppedOutgoingCount}",
        )
        handle.detachSubscriber(sub)
    }
}
