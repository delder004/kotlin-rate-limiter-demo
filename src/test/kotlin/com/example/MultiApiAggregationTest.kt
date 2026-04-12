package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ratelimiter.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class MultiApiAggregationTest {

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
    fun `multiple APIs fetched concurrently with independent rate limiters`() = runBlocking {
        val catLimiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
        val postLimiter = BurstyRateLimiter(permits = 5, per = 1.seconds)

        val (facts, posts) = coroutineScope {
            val factsJob = async {
                (1..4).map {
                    catLimiter.withPermit {
                        client.get("https://catfact.ninja/fact").body<CatFact>()
                    }
                }
            }
            val postsJob = async {
                (1..4).map { id ->
                    postLimiter.withPermit {
                        client.get("https://jsonplaceholder.typicode.com/posts/$id").body<JsonPlaceholderPost>()
                    }
                }
            }
            factsJob.await() to postsJob.await()
        }

        assertTrue(facts.size == 4, "Should have 4 cat facts")
        assertTrue(posts.size == 4, "Should have 4 posts")
        facts.forEach { assertTrue(it.fact.isNotBlank()) }
        posts.forEach { assertTrue(it.title.isNotBlank()) }
    }

    @Test
    fun `independent limiters do not block each other`() = runBlocking {
        // Fast limiter for one API, slow for another
        val fastLimiter = BurstyRateLimiter(permits = 10, per = 1.seconds)
        val slowLimiter = BurstyRateLimiter(permits = 2, per = 1.seconds)
        val mark = TimeSource.Monotonic.markNow()

        val (fastTime, slowTime) = coroutineScope {
            val fast = async {
                repeat(4) {
                    fastLimiter.withPermit {
                        client.get("https://jsonplaceholder.typicode.com/posts/${it + 1}")
                    }
                }
                mark.elapsedNow()
            }
            val slow = async {
                repeat(4) {
                    slowLimiter.withPermit {
                        client.get("https://catfact.ninja/fact")
                    }
                }
                mark.elapsedNow()
            }
            fast.await() to slow.await()
        }

        // The slow limiter (2/sec, 4 requests) should take noticeably longer
        assertTrue(slowTime > fastTime,
            "Slow limiter ($slowTime) should take longer than fast ($fastTime)")
    }
}
