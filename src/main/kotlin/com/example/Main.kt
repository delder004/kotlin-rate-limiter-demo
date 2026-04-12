package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ratelimiter.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Serializable
data class CatFact(val fact: String, val length: Int)

@Serializable
data class CatFactPage(val data: List<CatFact>, val last_page: Int, val current_page: Int)

@Serializable
data class JsonPlaceholderPost(val id: Int, val title: String, val userId: Int)

val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

fun main() = runBlocking {
    println("=== Rate Limiter Real-World Examples ===\n")

    demoPaginatedCrawl()
    demoMultiApiAggregation()
    demoFlowPipeline()
    demoRetryOnDenial()
    demoCompositeTieredLimits()
    demoWarmup()

    client.close()
    println("\nDone!")
}

/**
 * 1. Paginated crawl — fetch multiple pages of cat facts without blasting the API.
 */
suspend fun demoPaginatedCrawl() {
    println("--- 1. Paginated Crawl (3 req/sec) ---")
    val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
    val mark = TimeSource.Monotonic.markNow()
    val allFacts = mutableListOf<CatFact>()

    for (page in 1..5) {
        limiter.withPermit {
            val pageData = client.get("https://catfact.ninja/facts?page=$page&limit=3").body<CatFactPage>()
            allFacts.addAll(pageData.data)
            println("  Page $page [${mark.elapsedNow()}] — ${pageData.data.size} facts (total so far: ${allFacts.size})")
        }
    }
    println("  Crawled ${allFacts.size} facts across 5 pages\n")
}

/**
 * 2. Multi-API aggregation — hit catfact.ninja and JSONPlaceholder concurrently,
 *    each with its own rate limiter, then merge results.
 */
suspend fun demoMultiApiAggregation() = coroutineScope {
    println("--- 2. Multi-API Aggregation ---")
    val catLimiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
    val postLimiter = BurstyRateLimiter(permits = 5, per = 1.seconds)
    val mark = TimeSource.Monotonic.markNow()

    val factsJob = async {
        (1..4).map { i ->
            catLimiter.withPermit {
                val fact = client.get("https://catfact.ninja/fact").body<CatFact>()
                println("  [Cat]  #$i [${mark.elapsedNow()}] — ${fact.fact.take(50)}...")
                fact
            }
        }
    }

    val postsJob = async {
        (1..4).map { id ->
            postLimiter.withPermit {
                val post = client.get("https://jsonplaceholder.typicode.com/posts/$id").body<JsonPlaceholderPost>()
                println("  [Post] #$id [${mark.elapsedNow()}] — ${post.title.take(50)}")
                post
            }
        }
    }

    val facts = factsJob.await()
    val posts = postsJob.await()
    println("  Aggregated ${facts.size} cat facts + ${posts.size} posts\n")
}

/**
 * 3. Flow pipeline — use the Flow.rateLimit() extension to process a stream of IDs.
 */
suspend fun demoFlowPipeline() {
    println("--- 3. Flow Pipeline (rateLimit extension) ---")
    val limiter = BurstyRateLimiter(permits = 3, per = 1.seconds)
    val mark = TimeSource.Monotonic.markNow()

    val postIds = (1..8).asFlow()

    val titles = postIds
        .rateLimit(limiter)
        .map { id ->
            val post = client.get("https://jsonplaceholder.typicode.com/posts/$id").body<JsonPlaceholderPost>()
            println("  Post $id [${mark.elapsedNow()}] — ${post.title.take(60)}")
            post.title
        }
        .toList()

    println("  Processed ${titles.size} posts through flow pipeline\n")
}

/**
 * 4. Retry on denial — use tryAcquire for graceful degradation with a cache fallback.
 */
suspend fun demoRetryOnDenial() {
    println("--- 4. Retry on Denial (cache fallback) ---")
    val limiter = BurstyRateLimiter(permits = 2, per = 1.seconds)
    val cache = mutableMapOf<Int, String>()
    val mark = TimeSource.Monotonic.markNow()

    // Seed the cache
    cache[1] = "Cached: Cats sleep 70% of their lives."

    for (id in List(6) { (it % 3) + 1 }) {
        when (limiter.tryAcquire()) {
            is Permit.Granted -> {
                val fact = client.get("https://catfact.ninja/fact").body<CatFact>()
                cache[id] = fact.fact
                println("  [LIVE]  id=$id [${mark.elapsedNow()}] — ${fact.fact.take(60)}")
            }
            is Permit.Denied -> {
                val cached = cache[id] ?: "No cached data"
                println("  [CACHE] id=$id [${mark.elapsedNow()}] — ${cached.take(60)}")
            }
        }
    }
    println()
}

/**
 * 5. Composite tiered limits — 5/sec burst + 12/min sustained, like a real API.
 */
suspend fun demoCompositeTieredLimits() = coroutineScope {
    println("--- 5. Composite Tiered Limits (5/sec + 12/min) ---")
    val burstLimiter = BurstyRateLimiter(permits = 5, per = 1.seconds)
    val sustainedLimiter = BurstyRateLimiter(permits = 12, per = 1.minutes)
    val limiter = CompositeRateLimiter(burstLimiter, sustainedLimiter)
    val mark = TimeSource.Monotonic.markNow()

    // Fire 15 concurrent tasks — burst limit lets 5 through/sec,
    // but sustained limit caps total at 12
    val results = (1..15).map { i ->
        async {
            when (limiter.tryAcquire()) {
                is Permit.Granted -> {
                    val response = client.get("https://catfact.ninja/fact")
                    println("  Task $i [${mark.elapsedNow()}] — GRANTED (status=${response.status})")
                    true
                }
                is Permit.Denied -> {
                    println("  Task $i [${mark.elapsedNow()}] — DENIED (sustained limit hit)")
                    false
                }
            }
        }
    }.awaitAll()

    val granted = results.count { it }
    val denied = results.count { !it }
    println("  $granted granted, $denied denied by tiered limits\n")
}

/**
 * 6. Warm-up under load — SmoothRateLimiter with warmup gradually ramps up throughput.
 */
suspend fun demoWarmup() {
    println("--- 6. Warm-Up (5 req/sec, 3s warmup) ---")
    val limiter = SmoothRateLimiter(permits = 5, per = 1.seconds, warmup = 3.seconds)
    val mark = TimeSource.Monotonic.markNow()

    repeat(10) { i ->
        limiter.withPermit {
            val response = client.get("https://jsonplaceholder.typicode.com/posts/${i + 1}")
            println("  #${i + 1} [${mark.elapsedNow()}] status=${response.status}")
        }
    }
    println("  Notice: early requests are spaced further apart, then pace increases\n")
}
