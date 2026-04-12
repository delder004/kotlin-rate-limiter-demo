package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ratelimiter.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class CatFactApiTest {

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
    fun `rate limiter paces requests and all succeed`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
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
    fun `concurrent requests are properly throttled`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 5, per = 1.seconds)
        val mark = TimeSource.Monotonic.markNow()

        val results = coroutineScope {
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

        val maxElapsed = results.maxOf { it.first }
        assertTrue(maxElapsed >= 800.toLong().let { it.seconds / 1000 },
            "10 requests at 5/sec should take at least ~1s, took $maxElapsed")
    }

    @Test
    fun `smooth limiter spaces requests evenly`() = runBlocking {
        val limiter = SmoothRateLimiter(permits = 3, per = 1.seconds)
        val mark = TimeSource.Monotonic.markNow()
        val timestamps = mutableListOf<Long>()

        repeat(4) {
            limiter.withPermit {
                val response = client.get("https://catfact.ninja/fact")
                assertEquals(HttpStatusCode.OK, response.status)
                timestamps.add(mark.elapsedNow().inWholeMilliseconds)
            }
        }

        for (i in 1 until timestamps.size) {
            val gap = timestamps[i] - timestamps[i - 1]
            assertTrue(gap >= 250, "Gap between requests should be ~333ms, got ${gap}ms")
        }
    }

    @Test
    fun `tryAcquire prevents excess requests`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 2, per = 1.seconds)

        limiter.acquire()
        limiter.acquire()

        val permit = limiter.tryAcquire()
        assertTrue(permit is Permit.Denied, "Should be denied after exhausting permits")

        val denied = permit as Permit.Denied
        assertTrue(denied.retryAfter.inWholeMilliseconds > 0, "retryAfter should be positive")
    }

    @Test
    fun `composite limiter enforces stricter of two limits`() = runBlocking {
        val perSecond = BurstyRateLimiter(permits = 10, per = 1.seconds)
        val perBurst = BurstyRateLimiter(permits = 3, per = 1.seconds)
        val limiter = CompositeRateLimiter(perSecond, perBurst)
        val mark = TimeSource.Monotonic.markNow()

        val statuses = mutableListOf<Int>()
        repeat(5) {
            limiter.withPermit {
                val response = client.get("https://catfact.ninja/fact")
                statuses.add(response.status.value)
            }
        }

        assertTrue(statuses.all { it == 200 })

        val elapsed = mark.elapsedNow()
        assertTrue(elapsed >= 800.toLong().let { it.seconds / 1000 },
            "5 requests at 3/sec should take >1s, took $elapsed")
    }

    @Test
    fun `rate limiter headers decrease with requests`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 2, per = 1.seconds)
        val remainingValues = mutableListOf<String?>()

        repeat(2) {
            limiter.withPermit {
                val response = client.get("https://catfact.ninja/fact")
                remainingValues.add(response.headers["x-ratelimit-remaining"])
            }
        }

        val parsed = remainingValues.mapNotNull { it?.toIntOrNull() }
        if (parsed.size == 2) {
            assertTrue(parsed[0] > parsed[1],
                "Rate limit remaining should decrease: $parsed")
        }
    }
}
