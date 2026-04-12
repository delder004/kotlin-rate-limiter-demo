package com.example

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class FlowPipelineTest {

    @Test
    fun `flow rateLimit extension processes all items`() = runTest {
        val client = createTestClient()
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)

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
    fun `flow pipeline respects rate limit timing`() = runTest {
        val client = createTestClient()
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
        val mark = testScheduler.timeSource.markNow()

        val timestamps = (1..6).asFlow()
            .rateLimit(limiter)
            .map { id ->
                client.get("https://jsonplaceholder.typicode.com/posts/$id")
                mark.elapsedNow().inWholeMilliseconds
            }
            .toList()

        // 6 items at 3/sec: items 4-6 must wait for the next window.
        val totalSpan = timestamps.last() - timestamps.first()
        assertTrue(totalSpan >= 800, "6 items at 3/sec should span >=800ms, took ${totalSpan}ms")
    }

    @Test
    fun `flow pipeline can transform and filter with rate limiting`() = runTest {
        val client = createTestClient()
        val limiter = BurstyRateLimiter(permits = 5, per = 1.seconds, timeSource = testScheduler.timeSource)

        val posts = (1..10).asFlow()
            .rateLimit(limiter)
            .map { id ->
                client.get("https://jsonplaceholder.typicode.com/posts/$id").body<JsonPlaceholderPost>()
            }
            .toList()

        assertEquals(10, posts.size, "All 10 posts should be fetched")
        posts.forEach { assertTrue(it.title.isNotBlank()) }
    }
}
