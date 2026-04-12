package com.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RateLimiterTest {

    @Test
    fun `bursty limiter grants permits up to capacity`() = runTest {
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)

        // All 3 should be granted immediately
        repeat(3) {
            limiter.acquire()
        }
    }

    @Test
    fun `bursty limiter blocks when permits exhausted`() = runTest {
        val limiter = BurstyRateLimiter(permits = 2, per = 1.seconds, timeSource = testScheduler.timeSource)

        limiter.acquire()
        limiter.acquire()

        var acquired = false
        val job = launch {
            limiter.acquire()
            acquired = true
        }

        runCurrent()
        assertEquals(false, acquired, "Should be suspended waiting for permit")

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(true, acquired, "Should have acquired after time advance")

        job.join()
    }

    @Test
    fun `tryAcquire returns Denied when no permits available`() = runTest {
        val limiter = BurstyRateLimiter(permits = 1, per = 1.seconds, timeSource = testScheduler.timeSource)

        limiter.acquire()

        val result = limiter.tryAcquire()
        assertIs<Permit.Denied>(result)
    }

    @Test
    fun `tryAcquire returns Granted when permits available`() = runTest {
        val limiter = BurstyRateLimiter(permits = 5, per = 1.seconds, timeSource = testScheduler.timeSource)

        val result = limiter.tryAcquire()
        assertIs<Permit.Granted>(result)
    }

    @Test
    fun `smooth limiter spaces out permits evenly`() = runTest {
        val limiter = SmoothRateLimiter(permits = 5, per = 1.seconds, timeSource = testScheduler.timeSource)

        // First permit is immediate
        limiter.acquire()

        var acquired = false
        val job = launch {
            limiter.acquire()
            acquired = true
        }

        runCurrent()
        // With 5 permits/sec, interval is 200ms
        advanceTimeBy(199.milliseconds)
        runCurrent()
        assertEquals(false, acquired, "Should not yet have permit at 199ms")

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(true, acquired, "Should have permit at 200ms")

        job.join()
    }

    @Test
    fun `composite limiter enforces all sub-limiters`() = runTest {
        val fast = BurstyRateLimiter(permits = 10, per = 1.seconds, timeSource = testScheduler.timeSource)
        val slow = BurstyRateLimiter(permits = 2, per = 1.seconds, timeSource = testScheduler.timeSource)
        val limiter = CompositeRateLimiter(fast, slow)

        // The composite should be bound by the slower limiter (2/sec)
        limiter.acquire()
        limiter.acquire()

        val result = limiter.tryAcquire()
        assertIs<Permit.Denied>(result)
    }
}
