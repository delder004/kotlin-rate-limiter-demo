package com.example.simulation

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SimulationRegistryTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    private fun newRegistry(): SimulationRegistry {
        val counter = AtomicLong(0)
        return SimulationRegistry(clock = fixedClock, idGenerator = { "sim-${counter.incrementAndGet()}" })
    }

    private fun sampleConfig(): SimulationConfig =
        SimulationConfig(
            limiterType = LimiterType.BURSTY,
            permits = 5,
            perSeconds = 1.0,
            warmupSeconds = 0.0,
            requestsPerSecond = 5.0,
            overflowMode = OverflowMode.QUEUE,
            apiTarget = ApiTarget.NONE,
            serviceTimeMs = 50,
            jitterMs = 20,
            failureRate = 0.0,
            workerConcurrency = 50,
        )

    @Test
    fun `create returns handle with unique id and running status`() {
        val registry = newRegistry()
        val h1 = registry.create(sampleConfig())
        val h2 = registry.create(sampleConfig())

        assertNotEquals(h1.id, h2.id)
        assertEquals(SimulationStatus.RUNNING, h1.status)
        assertEquals(SimulationStatus.RUNNING, h2.status)
        assertTrue(h1.isRunning)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), h1.createdAt)
        assertNull(h1.stoppedAt)
    }

    @Test
    fun `get returns created handle`() {
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        val fetched = registry.get(created.id)
        assertNotNull(fetched)
        assertEquals(created.id, fetched.id)
    }

    @Test
    fun `get returns null for missing id`() {
        val registry = newRegistry()
        assertNull(registry.get("nope"))
    }

    @Test
    fun `stop transitions handle to stopped state and evicts it from the registry`() {
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        val stopped = registry.stop(created.id)
        assertNotNull(stopped)
        assertEquals(SimulationStatus.STOPPED, stopped.status)
        assertNotNull(stopped.stoppedAt)
        assertNull(registry.get(created.id), "stopped handles must not linger in the registry")
    }

    @Test
    fun `second stop of same id returns null after eviction`() {
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        assertNotNull(registry.stop(created.id))
        assertNull(registry.stop(created.id))
    }

    @Test
    fun `stop of missing id returns null`() {
        val registry = newRegistry()
        assertNull(registry.stop("missing"))
    }

    @Test
    fun `list returns all created handles`() {
        val registry = newRegistry()
        val a = registry.create(sampleConfig())
        val b = registry.create(sampleConfig())
        val ids = registry.list().map { it.id }.toSet()
        assertEquals(setOf(a.id, b.id), ids)
    }

    @Test
    fun `update preserves handle identity and changes config`() {
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        val newConfig =
            sampleConfig().copy(
                limiterType = LimiterType.SMOOTH,
                permits = 10,
                warmupSeconds = 2.0,
            )

        val result = registry.update(created.id, newConfig)
        assertTrue(result is SimulationRegistry.UpdateResult.Updated)
        val updated = (result as SimulationRegistry.UpdateResult.Updated).handle
        assertEquals(created.id, updated.id, "handle id should be preserved")
        assertSame(created, updated, "same handle instance should be returned")
        assertEquals(LimiterType.SMOOTH, updated.config.limiterType)
        assertEquals(10, updated.config.permits)
        assertEquals(SimulationStatus.RUNNING, updated.status)
        assertNotNull(updated.updatedAt)
    }

    @Test
    fun `update appends a Config Updated log entry describing what changed`() {
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        registry.update(created.id, sampleConfig().copy(requestsPerSecond = 42.0))
        val entry = created.recentLogs.firstOrNull { it.body.startsWith("Config Updated") }
        assertNotNull(entry, "expected a Config Updated audit log entry")
        assertTrue(
            "requestsPerSecond" in entry.body,
            "entry should name the changed field, got: ${entry.body}",
        )
    }

    @Test
    fun `update on unknown id returns NotFound`() {
        val registry = newRegistry()
        assertEquals(SimulationRegistry.UpdateResult.NotFound, registry.update("missing", sampleConfig()))
    }

    @Test
    fun `update after stop evicts handle and returns NotFound`() {
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        registry.stop(created.id)
        assertEquals(
            SimulationRegistry.UpdateResult.NotFound,
            registry.update(created.id, sampleConfig()),
        )
    }

    @Test
    fun `update refuses a handle whose status flipped to STOPPED mid-call`() {
        // Reproduces the PATCH/DELETE race: an update() that captured a handle
        // reference before stop() removed it from the map must not mutate the
        // handle or emit an Updated result. Simulate that window by leaving the
        // handle in the registry (as if remove hadn't happened yet on the other
        // thread) but flipping its status to STOPPED (as if stop's synchronized
        // block already ran).
        val registry = newRegistry()
        val created = registry.create(sampleConfig())
        created.status = SimulationStatus.STOPPED
        assertEquals(
            SimulationRegistry.UpdateResult.NotRunning,
            registry.update(created.id, sampleConfig()),
        )
    }

    @Test
    fun `update from queue to reject mode makes the running engine start denying under overload`() =
        runTest {
            val alwaysDenied =
                object : EngineLimiter {
                    override suspend fun acquire() {
                        delay(Long.MAX_VALUE / 2)
                    }

                    override fun tryAcquire(): EnginePermit = EnginePermit.Denied(retryAfterMs = 1000)
                }
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { alwaysDenied },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val registry =
                SimulationRegistry(
                    engine = engine,
                    clock = fixedClock,
                    idGenerator = { "sim-queue-reject" },
                )

            val queueConfig =
                sampleConfig().copy(
                    overflowMode = OverflowMode.QUEUE,
                    requestsPerSecond = 1000.0,
                    workerConcurrency = 2,
                )
            val handle = registry.create(queueConfig)

            advanceTimeBy(500)
            runCurrent()
            val beforeDenied = handle.currentMetrics.denied
            assertEquals(0L, beforeDenied, "queue mode should not deny, got $beforeDenied")

            val rejectConfig = queueConfig.copy(overflowMode = OverflowMode.REJECT)
            val result = registry.update(handle.id, rejectConfig)
            assertTrue(result is SimulationRegistry.UpdateResult.Updated)
            assertEquals(OverflowMode.REJECT, handle.config.overflowMode)

            advanceTimeBy(500)
            runCurrent()

            val afterDenied = handle.currentMetrics.denied
            assertTrue(
                afterDenied > 0,
                "after switching to reject mode under overload, denied should grow (got $afterDenied)",
            )
        }

    @Test
    fun `update from low rps to high rps increases producer pressure`() =
        runTest {
            val granted =
                object : EngineLimiter {
                    override suspend fun acquire() {}

                    override fun tryAcquire(): EnginePermit = EnginePermit.Granted
                }
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { granted },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 10,
                    metricsIntervalMs = 50,
                    random = Random(0),
                )
            val registry =
                SimulationRegistry(
                    engine = engine,
                    clock = fixedClock,
                    idGenerator = { "sim-rps" },
                )

            val lowConfig =
                sampleConfig().copy(
                    requestsPerSecond = 1.0,
                    serviceTimeMs = 10,
                    workerConcurrency = 4,
                    overflowMode = OverflowMode.QUEUE,
                )
            val handle = registry.create(lowConfig)

            advanceTimeBy(500)
            runCurrent()
            val lowAdmitted = handle.currentMetrics.admitted

            registry.update(handle.id, lowConfig.copy(requestsPerSecond = 500.0))
            advanceTimeBy(500)
            runCurrent()

            val highAdmitted = handle.currentMetrics.admitted
            assertTrue(
                highAdmitted > lowAdmitted,
                "high-rps epoch should admit more than low-rps epoch, got low=$lowAdmitted high=$highAdmitted",
            )
        }

    @Test
    fun `default id generator produces unique ids`() {
        val registry = SimulationRegistry()
        val ids = (1..50).map { registry.create(sampleConfig()).id }.toSet()
        assertEquals(50, ids.size)
    }
}
