package com.example.simulation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LimiterFactoryTest {
    private fun config(
        type: LimiterType,
        permits: Int = 3,
        perSeconds: Double = 1.0,
        warmupSeconds: Double = 0.0,
        compositeChildren: List<CompositeChild> = emptyList(),
    ) = SimulationConfig(
        limiterType = type,
        permits = permits,
        perSeconds = perSeconds,
        warmupSeconds = warmupSeconds,
        compositeChildren = compositeChildren,
        requestsPerSecond = 0.0,
        overflowMode = OverflowMode.QUEUE,
        apiTarget = ApiTarget.NONE,
        serviceTimeMs = 0,
        jitterMs = 0,
        failureRate = 0.0,
        workerConcurrency = 1,
    )

    @Test
    fun `bursty config creates bursty-shaped limiter`() {
        val raw = LimiterFactory.buildRateLimiter(config(LimiterType.BURSTY))
        val name = raw::class.qualifiedName.orEmpty()
        assertTrue("Bursty" in name, "expected Bursty impl, got $name")
    }

    @Test
    fun `smooth config creates smooth-shaped limiter`() {
        val raw =
            LimiterFactory.buildRateLimiter(
                config(LimiterType.SMOOTH, warmupSeconds = 2.0),
            )
        val name = raw::class.qualifiedName.orEmpty()
        assertTrue("Smooth" in name, "expected Smooth impl, got $name")
    }

    @Test
    fun `composite config creates composite-shaped limiter`() {
        val raw =
            LimiterFactory.buildRateLimiter(
                config(
                    LimiterType.COMPOSITE,
                    compositeChildren =
                        listOf(
                            CompositeChild(LimiterType.BURSTY, permits = 5, perSeconds = 1.0, warmupSeconds = 0.0),
                            CompositeChild(LimiterType.BURSTY, permits = 2, perSeconds = 60.0, warmupSeconds = 0.0),
                        ),
                ),
            )
        val name = raw::class.qualifiedName.orEmpty()
        assertTrue("Composite" in name, "expected Composite impl, got $name")
    }

    @Test
    fun `bursty limiter grants configured permits then denies`() =
        runTest {
            val limiter = LimiterFactory.create(config(LimiterType.BURSTY, permits = 3, perSeconds = 60.0))
            repeat(3) {
                val permit = limiter.tryAcquire()
                assertEquals(EnginePermit.Granted, permit, "permit $it should be granted")
            }
            val next = limiter.tryAcquire()
            assertTrue(next is EnginePermit.Denied, "expected Denied, got $next")
        }

    @Test
    fun `composite limiter respects stricter child tier`() =
        runTest {
            // Two children: 10/sec and 2/hour — the 2/hour child is the binding constraint.
            val limiter =
                LimiterFactory.create(
                    config(
                        LimiterType.COMPOSITE,
                        compositeChildren =
                            listOf(
                                CompositeChild(LimiterType.BURSTY, permits = 10, perSeconds = 1.0, warmupSeconds = 0.0),
                                CompositeChild(LimiterType.BURSTY, permits = 2, perSeconds = 3600.0, warmupSeconds = 0.0),
                            ),
                    ),
                )
            val first = limiter.tryAcquire()
            val second = limiter.tryAcquire()
            val third = limiter.tryAcquire()
            assertEquals(EnginePermit.Granted, first)
            assertEquals(EnginePermit.Granted, second)
            assertFalse(third is EnginePermit.Granted, "stricter child should cap at 2")
        }
}
