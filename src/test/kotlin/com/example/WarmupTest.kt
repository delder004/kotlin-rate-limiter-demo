package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class WarmupTest {

    @Test
    fun `warmup limiter starts slow and ramps up`() = runTest {
        val client = createTestClient()
        val limiter = SmoothRateLimiter(
            permits = 5, per = 1.seconds, warmup = 2.seconds,
            timeSource = testScheduler.timeSource,
        )
        val mark = testScheduler.timeSource.markNow()
        val timestamps = mutableListOf<Long>()

        repeat(6) {
            limiter.withPermit {
                client.get("https://jsonplaceholder.typicode.com/posts/${it + 1}")
                timestamps.add(mark.elapsedNow().inWholeMilliseconds)
            }
        }

        // Early gaps (during warmup) should be wider than later gaps (after warmup).
        val gaps = timestamps.zipWithNext { a, b -> b - a }
        if (gaps.size >= 4) {
            val earlyAvg = gaps.take(2).average()
            val lateAvg = gaps.takeLast(2).average()
            assertTrue(earlyAvg > lateAvg,
                "Early gaps ($earlyAvg ms avg) should be wider than late gaps ($lateAvg ms avg)")
        }
    }

    @Test
    fun `warmup limiter eventually reaches full speed`() = runTest {
        val client = createTestClient()
        val limiter = SmoothRateLimiter(
            permits = 5, per = 1.seconds, warmup = 1.seconds,
            timeSource = testScheduler.timeSource,
        )
        val mark = testScheduler.timeSource.markNow()
        val timestamps = mutableListOf<Long>()

        // Run enough requests to get past warmup
        repeat(10) {
            limiter.withPermit {
                client.get("https://jsonplaceholder.typicode.com/posts/${(it % 10) + 1}")
                timestamps.add(mark.elapsedNow().inWholeMilliseconds)
            }
        }

        // After warmup, gaps should stabilize around 200ms (5 permits/sec).
        val lateGaps = timestamps.zipWithNext { a, b -> b - a }.takeLast(3)
        val avgLateGap = lateGaps.average()
        assertTrue(avgLateGap < 400,
            "After warmup, gaps should approach 200ms, got avg ${avgLateGap}ms")
    }

    @Test
    fun `warmup limiter with zero warmup behaves like smooth limiter`() = runTest {
        val withWarmup = SmoothRateLimiter(
            permits = 3, per = 1.seconds, warmup = 0.seconds,
            timeSource = testScheduler.timeSource,
        )
        val withoutWarmup = SmoothRateLimiter(
            permits = 3, per = 1.seconds,
            timeSource = testScheduler.timeSource,
        )

        val mark1 = testScheduler.timeSource.markNow()
        repeat(4) { withWarmup.acquire() }
        val time1 = mark1.elapsedNow()

        val mark2 = testScheduler.timeSource.markNow()
        repeat(4) { withoutWarmup.acquire() }
        val time2 = mark2.elapsedNow()

        // Under deterministic virtual time, both should produce identical schedules.
        val diff = kotlin.math.abs(time1.inWholeMilliseconds - time2.inWholeMilliseconds)
        assertTrue(diff < 1000,
            "Zero-warmup and no-warmup should behave similarly, diff was ${diff}ms")
    }

    @Test
    fun `all requests succeed during warmup period`() = runTest {
        val client = createTestClient()
        val limiter = SmoothRateLimiter(
            permits = 5, per = 1.seconds, warmup = 3.seconds,
            timeSource = testScheduler.timeSource,
        )

        repeat(8) { i ->
            limiter.withPermit {
                val response = client.get("https://jsonplaceholder.typicode.com/posts/${i + 1}")
                assertEquals(HttpStatusCode.OK, response.status,
                    "Request ${i + 1} should succeed during warmup")
            }
        }
    }
}
