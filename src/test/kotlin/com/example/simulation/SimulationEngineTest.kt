package com.example.simulation

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ratelimiter.Permit
import ratelimiter.SmoothRateLimiter
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    // Bridges a kotlinx.coroutines TestCoroutineScheduler into a kotlin.time
    // TimeSource so SmoothRateLimiter's internal bucket math advances in
    // lockstep with the virtual coroutine clock used by runTest.
    private class SchedulerTimeSource(private val scheduler: TestCoroutineScheduler) : TimeSource {
        override fun markNow(): TimeMark {
            val markedAt = scheduler.currentTime
            return object : TimeMark {
                override fun elapsedNow(): Duration = (scheduler.currentTime - markedAt).milliseconds
            }
        }
    }

    // Mimics SmoothRateLimiter semantics: bucket capacity = 1 permit, refilled
    // every `refillIntervalMs` from the injected engine clock.
    private class FakeSmoothLimiter(
        private val refillIntervalMs: Long,
        private val now: () -> Long,
    ) : EngineLimiter {
        private var available: Int = 1
        private var nextRefillAt: Long = Long.MIN_VALUE

        override suspend fun acquire() {}

        override fun tryAcquire(): EnginePermit {
            val t = now()
            if (nextRefillAt == Long.MIN_VALUE) nextRefillAt = t
            if (t >= nextRefillAt) {
                available = 1
                nextRefillAt = t + refillIntervalMs
            }
            return if (available > 0) {
                available--
                EnginePermit.Granted
            } else {
                EnginePermit.Denied(retryAfterMs = nextRefillAt - t)
            }
        }
    }

    @Test
    fun `smooth limiter admits at configured rate when offered load exceeds it`() =
        runTest {
            // Smooth-like limiter refilling every 4ms (~250/s). Offered load
            // is 500/s. Producer tick is 20ms → 10 arrivals per tick. The
            // producer must space those 10 arrivals across the tick window,
            // otherwise the bucket (capacity=1) only admits 1 per tick = 50/s.
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { FakeSmoothLimiter(refillIntervalMs = 4, now = { testScheduler.currentTime }) },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 20,
                    metricsIntervalMs = 100,
                    random = Random(0),
                )
            val h = handle(baseConfig(overflowMode = OverflowMode.REJECT, requestsPerSecond = 500.0))
            engine.start(h)
            advanceTimeBy(1000)
            runCurrent()

            val metrics = h.currentMetrics
            // Effective admission ceiling is 250/s. Fix is valid iff we clear
            // 200/s — well above the 50/s ceiling imposed by tick bursting.
            assertTrue(
                metrics.admitted >= 200,
                "expected smooth limiter to admit ~250/s, got ${metrics.admitted} in 1s",
            )
            assertTrue(
                metrics.denied > 0,
                "expected some denials at 500/s offered vs ~250/s capacity, got ${metrics.denied}",
            )
        }

    @Test
    fun `real SmoothRateLimiter admits near configured rate under sustained overload`() =
        runTest {
            // Drive the real library SmoothRateLimiter with 500 RPS offered
            // against a configured 250 permits/sec, using the same clock the
            // coroutine scheduler advances. This pins the slider→limiter
            // mapping end-to-end: after the pacing fix we should admit close
            // to 250/s, not the 50/s tick-burst ceiling.
            val vts = SchedulerTimeSource(testScheduler)
            val smooth =
                SmoothRateLimiter(
                    permits = 250,
                    per = 1.seconds,
                    warmup = Duration.ZERO,
                    timeSource = vts,
                )
            val engineLimiter =
                object : EngineLimiter {
                    override suspend fun acquire() {
                        smooth.acquire()
                    }

                    override fun tryAcquire(): EnginePermit =
                        when (val p = smooth.tryAcquire()) {
                            is Permit.Granted -> EnginePermit.Granted
                            is Permit.Denied -> EnginePermit.Denied(retryAfterMs = p.retryAfter.inWholeMilliseconds)
                        }
                }
            val engine =
                CoroutineSimulationEngine(
                    scope = backgroundScope,
                    limiterFactory = { engineLimiter },
                    timeSource = { testScheduler.currentTime },
                    tickMs = 20,
                    metricsIntervalMs = 100,
                    random = Random(0),
                )
            val h = handle(baseConfig(overflowMode = OverflowMode.REJECT, requestsPerSecond = 500.0))
            engine.start(h)
            advanceTimeBy(2000)
            runCurrent()

            val metrics = h.currentMetrics
            // Over ~2s we offer ~1000 requests and expect roughly 500 admits
            // at the 250/s steady-state. Accept a generous band but insist on
            // clearing 400 so the old 50/s ceiling (≤100 in 2s) cannot pass.
            assertTrue(
                metrics.admitted in 400..600,
                "expected real smooth limiter to admit ~500 in 2s, got ${metrics.admitted}",
            )
            assertTrue(
                metrics.denied > 0,
                "expected denials at 500/s offered vs 250/s capacity, got ${metrics.denied}",
            )
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
