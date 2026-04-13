package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CatFactApiTest {
    @Test
    fun `rate limiter paces requests and all succeed`() =
        runTest {
            val client = createTestClient()
            val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
            val statuses = mutableListOf<Int>()

            repeat(5) {
                limiter.withPermit {
                    val response = client.get("https://catfact.ninja/fact")
                    statuses.add(response.status.value)
                }
            }

            assertTrue(statuses.all { it == 200 }, "All requests should return 200, got: $statuses")
        }

    @Test
    fun `concurrent requests are properly throttled`() =
        runTest {
            val client = createTestClient()
            val limiter = BurstyRateLimiter(permits = 5, per = 1.seconds, timeSource = testScheduler.timeSource)
            val mark = testScheduler.timeSource.markNow()

            val results =
                coroutineScope {
                    (1..10).map {
                        async {
                            limiter.withPermit {
                                val response = client.get("https://catfact.ninja/fact")
                                mark.elapsedNow() to response.status.value
                            }
                        }
                    }.awaitAll()
                }

            assertTrue(results.all { it.second == 200 }, "All should be 200")

            val maxElapsedMs = results.maxOf { it.first.inWholeMilliseconds }
            assertTrue(
                maxElapsedMs >= 800L,
                "10 requests at 5/sec should take at least ~1s, took ${maxElapsedMs}ms",
            )
        }

    @Test
    fun `smooth limiter spaces requests evenly`() =
        runTest {
            val client = createTestClient()
            val limiter = SmoothRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
            val mark = testScheduler.timeSource.markNow()
            val timestamps = mutableListOf<Long>()

            repeat(4) {
                limiter.withPermit {
                    val response = client.get("https://catfact.ninja/fact")
                    assertEquals(HttpStatusCode.OK, response.status)
                    timestamps.add(mark.elapsedNow().inWholeMilliseconds)
                }
            }

            // Smooth limiter: 3 permits/sec → ~333ms spacing. First permit is immediate.
            for (i in 1 until timestamps.size) {
                val gap = timestamps[i] - timestamps[i - 1]
                assertTrue(gap >= 250, "Gap between requests should be ~333ms, got ${gap}ms")
            }
        }

    @Test
    fun `tryAcquire prevents excess requests`() =
        runTest {
            val limiter = BurstyRateLimiter(permits = 2, per = 1.seconds, timeSource = testScheduler.timeSource)

            limiter.acquire()
            limiter.acquire()

            val permit = limiter.tryAcquire()
            assertTrue(permit is Permit.Denied, "Should be denied after exhausting permits")

            val denied = permit as Permit.Denied
            assertTrue(denied.retryAfter.inWholeMilliseconds > 0, "retryAfter should be positive")
        }

    @Test
    fun `composite limiter enforces stricter of two limits`() =
        runTest {
            val client = createTestClient()
            val perSecond = BurstyRateLimiter(permits = 10, per = 1.seconds, timeSource = testScheduler.timeSource)
            val perBurst = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
            val limiter = CompositeRateLimiter(perSecond, perBurst)
            val mark = testScheduler.timeSource.markNow()

            val statuses = mutableListOf<Int>()
            repeat(5) {
                limiter.withPermit {
                    val response = client.get("https://catfact.ninja/fact")
                    statuses.add(response.status.value)
                }
            }

            assertTrue(statuses.all { it == 200 })

            // Composite uses the tighter 3/sec burst: 5 requests ⇒ 2 refills ≈ 666ms.
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            assertTrue(
                elapsedMs >= 600L,
                "5 requests at 3/sec should take >=600ms, took ${elapsedMs}ms",
            )
        }
}
