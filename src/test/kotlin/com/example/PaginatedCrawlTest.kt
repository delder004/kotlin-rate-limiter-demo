package com.example

import io.ktor.client.*
import io.ktor.client.call.*
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class PaginatedCrawlTest {

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
    fun `paginated crawl fetches all pages at controlled rate`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
        val mark = TimeSource.Monotonic.markNow()
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

        // With 3 permits/sec, pages 4-5 should be delayed by at least ~1s from the start
        val totalTime = timestamps.last() - timestamps.first()
        assertTrue(totalTime >= 800, "5 pages at 3/sec should span >800ms, took ${totalTime}ms")
    }

    @Test
    fun `paginated crawl returns valid data on each page`() = runBlocking {
        val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)

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
