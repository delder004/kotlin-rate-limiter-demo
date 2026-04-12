package com.example

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Creates an HttpClient with MockEngine that returns deterministic canned JSON
 * for the URL shapes used by the demos and tests.
 *
 * `delay(serviceDelayMs)` inside the handler runs under the caller's dispatcher,
 * so under `runTest` it becomes virtual time and can be advanced with
 * `testScheduler.advanceTimeBy`.
 */
fun createTestClient(
    serviceDelayMs: Long = 0L,
    failureRate: Double = 0.0,
): HttpClient {
    var requestCount = 0
    return HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                if (serviceDelayMs > 0) delay(serviceDelayMs)
                requestCount++
                val shouldFail = failureRate > 0.0 &&
                    (requestCount * failureRate).toInt() >
                        ((requestCount - 1) * failureRate).toInt()
                if (shouldFail) {
                    respond(
                        content = """{"error":"simulated failure"}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                } else {
                    respondToUrl(request.url)
                }
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

private fun MockRequestHandleScope.respondToUrl(url: Url): HttpResponseData {
    val path = url.encodedPath
    val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    return when {
        // catfact.ninja/facts?page=N&limit=M — paginated page of CatFact
        path.contains("/facts") -> {
            val page = url.parameters["page"]?.toIntOrNull() ?: 1
            val limit = url.parameters["limit"]?.toIntOrNull() ?: 3
            val facts = (1..limit).joinToString(",") { i ->
                val idx = (page - 1) * limit + i
                """{"fact":"Test fact $idx about cats","length":${20 + i}}"""
            }
            respond(
                """{"data":[$facts],"current_page":$page,"last_page":10}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        // catfact.ninja/fact — single CatFact
        path.contains("/fact") -> {
            respond(
                """{"fact":"Cats sleep for 70% of their lives.","length":34}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        // jsonplaceholder.typicode.com/posts/N — single post
        path.matches(Regex(".*/posts/\\d+$")) -> {
            val id = path.substringAfterLast("/").toIntOrNull() ?: 1
            respond(
                """{"id":$id,"title":"Test post title $id","userId":1}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        // jsonplaceholder.typicode.com/posts (list)
        path.endsWith("/posts") -> {
            respond(
                """[{"id":1,"title":"First post","userId":1}]""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        else -> respond("Not Found", HttpStatusCode.NotFound)
    }
}
