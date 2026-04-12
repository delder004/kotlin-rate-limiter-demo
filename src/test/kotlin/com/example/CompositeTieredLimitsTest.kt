package com.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ratelimiter.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CompositeTieredLimitsTest {

    private lateinit var client: HttpClient

    @BeforeTest
    fun setup() {
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @AfterTest
    fun teardown() {
        client.close()
    }

    @Test
    fun `composite limiter caps total requests at sustained limit`() = runBlocking {
        val burstLimiter = BurstyRateLimiter(permits = 5, per = 1.seconds)
        val sustainedLimiter = BurstyRateLimiter(permits = 8, per = 1.minutes)
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
    fun `composite limiter respects burst limit within sustained window`() = runBlocking {
        val burstLimiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
        val sustainedLimiter = BurstyRateLimiter(permits = 20, per = 1.minutes)
        val limiter = CompositeRateLimiter(burstLimiter, sustainedLimiter)
        val mark = TimeSource.Monotonic.markNow()

        // Make 6 requests using acquire (blocking) — burst limit of 3/sec means
        // requests 4-6 should be delayed to the next second
        repeat(6) {
            limiter.withPermit {
                client.get("https://jsonplaceholder.typicode.com/posts/${it + 1}")
            }
        }

        val elapsed = mark.elapsedNow()
        assertTrue(elapsed >= 800.toLong().let { it.seconds / 1000 },
            "6 requests at 3/sec burst should take >800ms, took $elapsed")
    }

    @Test
    fun `tiered tryAcquire denial comes from the exhausted tier`() = runBlocking {
        // Very tight sustained limit
        val burstLimiter = BurstyRateLimiter(permits = 10, per = 1.seconds)
        val sustainedLimiter = BurstyRateLimiter(permits = 3, per = 1.minutes)
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
