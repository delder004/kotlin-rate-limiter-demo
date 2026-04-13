package com.example

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import ratelimiter.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class MultiApiAggregationTest {
    @Test
    fun `multiple APIs fetched concurrently with independent rate limiters`() =
        runTest {
            val client = createTestClient()
            val catLimiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
            val postLimiter = BurstyRateLimiter(permits = 5, per = 1.seconds, timeSource = testScheduler.timeSource)

            val (facts, posts) =
                coroutineScope {
                    val factsJob =
                        async {
                            (1..4).map {
                                catLimiter.withPermit {
                                    client.get("https://catfact.ninja/fact").body<CatFact>()
                                }
                            }
                        }
                    val postsJob =
                        async {
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
    fun `independent limiters do not block each other`() =
        runTest {
            val client = createTestClient()
            val fastLimiter = BurstyRateLimiter(permits = 10, per = 1.seconds, timeSource = testScheduler.timeSource)
            val slowLimiter = BurstyRateLimiter(permits = 2, per = 1.seconds, timeSource = testScheduler.timeSource)
            val mark = testScheduler.timeSource.markNow()

            val (fastTime, slowTime) =
                coroutineScope {
                    val fast =
                        async {
                            repeat(4) {
                                fastLimiter.withPermit {
                                    client.get("https://jsonplaceholder.typicode.com/posts/${it + 1}")
                                }
                            }
                            mark.elapsedNow()
                        }
                    val slow =
                        async {
                            repeat(4) {
                                slowLimiter.withPermit {
                                    client.get("https://catfact.ninja/fact")
                                }
                            }
                            mark.elapsedNow()
                        }
                    fast.await() to slow.await()
                }

            // The slow limiter (2/sec, 4 requests) must wait for 2 permit windows.
            assertTrue(
                slowTime > fastTime,
                "Slow limiter ($slowTime) should take longer than fast ($fastTime)",
            )
        }
}
