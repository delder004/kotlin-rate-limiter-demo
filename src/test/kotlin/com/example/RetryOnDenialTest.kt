package com.example

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RetryOnDenialTest {
    @Test
    fun `tryAcquire serves cached data when rate limited`() =
        runTest {
            val client = createTestClient()
            val limiter = BurstyRateLimiter(permits = 2, per = 1.seconds, timeSource = testScheduler.timeSource)
            val cache = mutableMapOf<String, CatFact>()
            val liveHits = mutableListOf<String>()
            val cacheHits = mutableListOf<String>()

            // Seed cache
            cache["seed"] = CatFact("Cats have 9 lives (cached)", 26)

            // Try 6 rapid requests — only 2 should go live, rest from cache
            repeat(6) { i ->
                val key = if (i == 0) "seed" else "req-$i"
                when (limiter.tryAcquire()) {
                    is Permit.Granted -> {
                        val fact = client.get("https://catfact.ninja/fact").body<CatFact>()
                        cache[key] = fact
                        liveHits.add(key)
                    }
                    is Permit.Denied -> {
                        val cached = cache.values.firstOrNull()
                        assertTrue(cached != null, "Cache should have at least the seeded value")
                        cacheHits.add(key)
                    }
                }
            }

            assertTrue(liveHits.size <= 2, "At most 2 live requests should have been made, got ${liveHits.size}")
            assertTrue(cacheHits.size >= 4, "At least 4 should have been served from cache, got ${cacheHits.size}")
        }

    @Test
    fun `denied permit provides meaningful retryAfter duration`() =
        runTest {
            val limiter = BurstyRateLimiter(permits = 1, per = 1.seconds, timeSource = testScheduler.timeSource)

            // Use the one permit
            limiter.acquire()

            val permit = limiter.tryAcquire()
            assertTrue(permit is Permit.Denied)

            val denied = permit as Permit.Denied
            assertTrue(
                denied.retryAfter.inWholeMilliseconds in 1..1100,
                "retryAfter should be roughly <=1s, got ${denied.retryAfter}",
            )
        }

    @Test
    fun `cache gets updated when permits are available`() =
        runTest {
            val client = createTestClient()
            val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
            val cache = mutableMapOf<Int, String>()

            // With 3 permits, first 3 requests should all be live and update the cache
            for (i in 1..3) {
                when (limiter.tryAcquire()) {
                    is Permit.Granted -> {
                        val fact = client.get("https://catfact.ninja/fact").body<CatFact>()
                        cache[i] = fact.fact
                    }
                    is Permit.Denied -> {
                        // Should not happen for first 3
                    }
                }
            }

            assertTrue(cache.size == 3, "All 3 requests should have gone live and cached, got ${cache.size}")
            cache.values.forEach { assertTrue(it.isNotBlank()) }
        }
}
