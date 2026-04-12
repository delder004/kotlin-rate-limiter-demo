package com.example

import io.ktor.client.request.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CompositeTieredLimitsTest {

    @Test
    fun `composite limiter caps total requests at sustained limit`() = runTest {
        val burstLimiter = BurstyRateLimiter(permits = 5, per = 1.seconds, timeSource = testScheduler.timeSource)
        val sustainedLimiter = BurstyRateLimiter(permits = 8, per = 1.minutes, timeSource = testScheduler.timeSource)
        val limiter = CompositeRateLimiter(burstLimiter, sustainedLimiter)

        var granted = 0
        var denied = 0

        repeat(12) {
            when (limiter.tryAcquire()) {
                is Permit.Granted -> granted++
                is Permit.Denied -> denied++
            }
        }

        // Sustained limit is 8/min — at most 8 should be granted
        assertTrue(granted <= 8, "Sustained limit should cap at 8, but granted $granted")
        assertTrue(denied >= 4, "At least 4 should be denied, but only $denied denied")
    }

    @Test
    fun `composite limiter respects burst limit within sustained window`() = runTest {
        val client = createTestClient()
        val burstLimiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
        val sustainedLimiter = BurstyRateLimiter(permits = 20, per = 1.minutes, timeSource = testScheduler.timeSource)
        val limiter = CompositeRateLimiter(burstLimiter, sustainedLimiter)
        val mark = testScheduler.timeSource.markNow()

        // 6 requests with a 3/sec burst: requests 4-6 must wait for the next second.
        repeat(6) {
            limiter.withPermit {
                client.get("https://jsonplaceholder.typicode.com/posts/${it + 1}")
            }
        }

        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        assertTrue(elapsedMs >= 800L,
            "6 requests at 3/sec burst should take >=800ms, took ${elapsedMs}ms")
    }

    @Test
    fun `tiered tryAcquire denial comes from the exhausted tier`() = runTest {
        // Very tight sustained limit
        val burstLimiter = BurstyRateLimiter(permits = 10, per = 1.seconds, timeSource = testScheduler.timeSource)
        val sustainedLimiter = BurstyRateLimiter(permits = 3, per = 1.minutes, timeSource = testScheduler.timeSource)
        val limiter = CompositeRateLimiter(burstLimiter, sustainedLimiter)

        // Exhaust the sustained limit
        repeat(3) { limiter.acquire() }

        val permit = limiter.tryAcquire()
        assertTrue(permit is Permit.Denied, "Should be denied after sustained limit exhausted")

        // The retryAfter should be long (close to 1 minute) since it's the sustained limit
        val denied = permit as Permit.Denied
        assertTrue(denied.retryAfter.inWholeSeconds >= 10,
            "retryAfter should reflect the sustained window, got ${denied.retryAfter}")
    }
}
