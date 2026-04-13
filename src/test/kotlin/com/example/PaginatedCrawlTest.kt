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
class PaginatedCrawlTest {
    @Test
    fun `paginated crawl fetches all pages at controlled rate`() =
        runTest {
            val client = createTestClient(serviceDelayMs = 10)
            val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)
            val mark = testScheduler.timeSource.markNow()
            val allFacts = mutableListOf<CatFact>()
            val timestamps = mutableListOf<Long>()

            for (page in 1..5) {
                limiter.withPermit {
                    val pageData = client.get("https://catfact.ninja/facts?page=$page&limit=3").body<CatFactPage>()
                    allFacts.addAll(pageData.data)
                    timestamps.add(mark.elapsedNow().inWholeMilliseconds)
                }
            }

            // Should have fetched facts from all 5 pages
            assertTrue(allFacts.size >= 5, "Should have at least 5 facts from 5 pages, got ${allFacts.size}")

            // With 3 permits/sec: first 3 burst, then 1 refill per 333ms.
            // 5 pages ⇒ last two waits add 2 × 333 ≈ 666ms span.
            val totalTime = timestamps.last() - timestamps.first()
            assertTrue(totalTime >= 600, "5 pages at 3/sec should span >=600ms, took ${totalTime}ms")
        }

    @Test
    fun `paginated crawl returns valid data on each page`() =
        runTest {
            val client = createTestClient()
            val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds, timeSource = testScheduler.timeSource)

            for (page in 1..3) {
                limiter.withPermit {
                    val pageData = client.get("https://catfact.ninja/facts?page=$page&limit=5").body<CatFactPage>()
                    assertTrue(pageData.data.isNotEmpty(), "Page $page should have facts")
                    assertTrue(pageData.current_page == page, "Should be page $page")
                    pageData.data.forEach { fact ->
                        assertTrue(fact.fact.isNotBlank(), "Fact text should not be blank")
                        assertTrue(fact.length > 0, "Fact length should be positive")
                    }
                }
            }
        }
}
