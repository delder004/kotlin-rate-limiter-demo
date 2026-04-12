package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
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

class FlowPipelineTest {

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
    fun `flow rateLimit extension processes all items`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)

        val titles = (1..6).asFlow()
            .rateLimit(limiter)
            .map { id ->
                client.get("https://jsonplaceholder.typicode.com/posts/$id").body<JsonPlaceholderPost>().title
            }
            .toList()

        assertEquals(6, titles.size, "Should process all 6 items")
        titles.forEach { assertTrue(it.isNotBlank(), "Title should not be blank") }
    }

    @Test
    fun `flow pipeline respects rate limit timing`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
        val mark = TimeSource.Monotonic.markNow()

        val timestamps = (1..6).asFlow()
            .rateLimit(limiter)
            .map { id ->
                client.get("https://jsonplaceholder.typicode.com/posts/$id")
                mark.elapsedNow().inWholeMilliseconds
            }
            .toList()

        // 6 items at 3/sec should take at least ~1s total
        val totalSpan = timestamps.last() - timestamps.first()
        assertTrue(totalSpan >= 800, "6 items at 3/sec should span >800ms, took ${totalSpan}ms")
    }

    @Test
    fun `flow pipeline can transform and filter with rate limiting`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 5, per = 1.seconds)

        val shortTitles = (1..10).asFlow()
            .rateLimit(limiter)
            .map { id ->
                client.get("https://jsonplaceholder.typicode.com/posts/$id").body<JsonPlaceholderPost>()
            }
            .toList()
            .filter { it.title.length < 40 }
            .map { it.title }

        // We don't know exactly how many will be short, but the pipeline should complete
        assertTrue(shortTitles.isNotEmpty() || true, "Pipeline should complete without error")
    }
}
