package com.example.web

import com.example.module
import com.example.simulation.ApiTarget
import com.example.simulation.LimiterType
import com.example.simulation.OverflowMode
import com.example.simulation.SimulationRegistry
import com.example.simulation.SimulationStatus
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutesTest {
    private fun validConfigParams(): Parameters =
        parameters {
            append("limiterType", "bursty")
            append("permits", "5")
            append("perSeconds", "1.0")
            append("warmupSeconds", "0")
            append("requestsPerSecond", "5.0")
            append("overflowMode", "queue")
            append("apiTarget", "none")
            append("serviceTimeMs", "50")
            append("jitterMs", "20")
            append("failureRate", "0.0")
            append("workerConcurrency", "50")
        }

    @Test
    fun `GET returns 200 HTML page shell`() =
        testApplication {
            application { module() }
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            val contentType = response.contentType()
            assertTrue(
                contentType != null && contentType.match(ContentType.Text.Html),
                "expected HTML content-type, got $contentType",
            )
            val body = response.bodyAsText()
            assertTrue("Kotlin Rate Limiter Demo" in body)
            assertTrue("id=\"page-root\"" in body)
            assertTrue("id=\"step-limiter\"" in body)
            assertTrue("id=\"stats-panel\"" in body)
            assertTrue("id=\"chart-panel\"" in body)
            assertTrue("id=\"log-panel\"" in body)
        }

    @Test
    fun `POST simulations with valid form creates handle and patches sim lifecycle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val response = client.submitForm(url = "/simulations", formParameters = validConfigParams())

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("text/event-stream", response.contentType()?.withoutParameters()?.toString())

            val body = response.bodyAsText()
            assertTrue("datastar-merge-signals" in body, "expected signal patch event")
            assertTrue("datastar-merge-fragments" in body, "expected fragment patch event")
            assertTrue("\"running\":true" in body)
            assertTrue("\"status\":\"running\"" in body)

            val handles = registry.list()
            assertEquals(1, handles.size)
            val handle = handles.single()
            assertTrue("\"id\":\"${handle.id}\"" in body, "handle id should be patched into signals")
        }

    @Test
    fun `POST simulations with invalid form returns form errors and creates no handle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val response =
                client.submitForm(
                    url = "/simulations",
                    formParameters =
                        parameters {
                            append("limiterType", "nonsense")
                            append("permits", "0")
                            append("perSeconds", "1.0")
                            append("warmupSeconds", "0")
                            append("requestsPerSecond", "5.0")
                            append("overflowMode", "queue")
                            append("apiTarget", "none")
                            append("serviceTimeMs", "50")
                            append("jitterMs", "20")
                            append("failureRate", "0.0")
                            append("workerConcurrency", "50")
                        },
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("datastar-merge-fragments" in body)
            assertTrue("id=\"errors-form\"" in body)
            assertTrue("limiterType" in body)
            assertTrue("permits" in body)
            assertTrue("\"running\":true" !in body, "should not transition to running")
            assertEquals(0, registry.list().size)
        }

    @Test
    fun `DELETE simulations stops an existing handle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()

            val response = client.delete("/simulations/${handle.id}")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("\"running\":false" in body)
            assertTrue("\"status\":\"stopped\"" in body)
            assertTrue("\"${handle.id}\"" in body, "stopped response should retain sim.id for resume")

            val stored = registry.get(handle.id)
            assertNotNull(stored)
            assertEquals(SimulationStatus.STOPPED, stored.status)
        }

    @Test
    fun `DELETE simulations returns 404 for missing handle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val response = client.delete("/simulations/does-not-exist")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertNull(registry.get("does-not-exist"))
        }

    @Test
    fun `PATCH simulations with valid form updates running handle preserving id`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()
            val originalId = handle.id

            val response =
                client.submitForm(
                    url = "/simulations/${handle.id}",
                    formParameters =
                        parameters {
                            append("limiterType", "smooth")
                            append("permits", "10")
                            append("perSeconds", "1.0")
                            append("warmupSeconds", "2")
                            append("requestsPerSecond", "25.0")
                            append("overflowMode", "queue")
                            append("apiTarget", "none")
                            append("serviceTimeMs", "50")
                            append("jitterMs", "20")
                            append("failureRate", "0.0")
                            append("workerConcurrency", "50")
                        },
                ) { method = HttpMethod.Patch }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("datastar-merge-signals" in body)
            assertTrue("\"id\":\"$originalId\"" in body)

            val updated = registry.get(originalId)
            assertNotNull(updated)
            assertEquals(originalId, updated.id, "id must be preserved across update")
            assertEquals(LimiterType.SMOOTH, updated.config.limiterType)
            assertEquals(10, updated.config.permits)
        }

    @Test
    fun `PATCH simulations with invalid form returns form errors and leaves config untouched`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()
            val originalLimiterType = handle.config.limiterType

            val response =
                client.submitForm(
                    url = "/simulations/${handle.id}",
                    formParameters =
                        parameters {
                            append("limiterType", "nonsense")
                            append("permits", "0")
                            append("perSeconds", "1.0")
                            append("warmupSeconds", "0")
                            append("requestsPerSecond", "5.0")
                            append("overflowMode", "queue")
                            append("apiTarget", "none")
                            append("serviceTimeMs", "50")
                            append("jitterMs", "20")
                            append("failureRate", "0.0")
                            append("workerConcurrency", "50")
                        },
                ) { method = HttpMethod.Patch }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("id=\"errors-form\"" in body)
            assertTrue("limiterType" in body)
            assertTrue("permits" in body)

            val stored = registry.get(handle.id)!!
            assertEquals(originalLimiterType, stored.config.limiterType, "invalid update must not change config")
            assertEquals(SimulationStatus.RUNNING, stored.status)
        }

    private val validDatastarJsonBody: String =
        """
        {
          "sim": { "id": null, "status": "idle", "running": false },
          "config": {
            "limiterType": "bursty",
            "permits": 5,
            "perSeconds": 1.0,
            "warmupSeconds": 0.0,
            "requestsPerSecond": 5.0,
            "overflowMode": "queue",
            "apiTarget": "none",
            "serviceTimeMs": 50,
            "jitterMs": 20,
            "failureRate": 0.0,
            "workerConcurrency": 50
          },
          "stats": {},
          "errors": { "form": null, "stream": null }
        }
        """.trimIndent()

    @Test
    fun `POST simulations accepts Datastar nested JSON payload`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val response =
                client.post("/simulations") {
                    contentType(ContentType.Application.Json)
                    setBody(validDatastarJsonBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("\"running\":true" in body, "expected transition to running")
            assertTrue("\"status\":\"running\"" in body)
            assertEquals(1, registry.list().size)
        }

    @Test
    fun `POST simulations with invalid Datastar JSON returns form errors`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val body =
                """
                {
                  "config": {
                    "limiterType": "nonsense",
                    "permits": 0,
                    "perSeconds": 1.0,
                    "warmupSeconds": 0,
                    "requestsPerSecond": 5.0,
                    "overflowMode": "queue",
                    "apiTarget": "none",
                    "serviceTimeMs": 50,
                    "jitterMs": 20,
                    "failureRate": 0.0,
                    "workerConcurrency": 50
                  }
                }
                """.trimIndent()

            val response =
                client.post("/simulations") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val text = response.bodyAsText()
            assertTrue("id=\"errors-form\"" in text)
            assertTrue("limiterType" in text)
            assertTrue("permits" in text)
            assertTrue("\"running\":true" !in text)
            assertEquals(0, registry.list().size)
        }

    @Test
    fun `PATCH simulations accepts Datastar nested JSON payload`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()
            val originalId = handle.id

            val patchBody =
                """
                {
                  "config": {
                    "limiterType": "smooth",
                    "permits": 10,
                    "perSeconds": 1.0,
                    "warmupSeconds": 2,
                    "requestsPerSecond": 25.0,
                    "overflowMode": "queue",
                    "apiTarget": "none",
                    "serviceTimeMs": 50,
                    "jitterMs": 20,
                    "failureRate": 0.0,
                    "workerConcurrency": 50
                  }
                }
                """.trimIndent()

            val response =
                client.patch("/simulations/${handle.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(patchBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("\"id\":\"$originalId\"" in body)

            val updated = registry.get(originalId)!!
            assertEquals(LimiterType.SMOOTH, updated.config.limiterType)
            assertEquals(10, updated.config.permits)
        }

    @Test
    fun `PATCH simulations applies overflowMode change from running handle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()

            val patchBody =
                """
                {
                  "config": {
                    "limiterType": "bursty",
                    "permits": 5,
                    "perSeconds": 1.0,
                    "warmupSeconds": 0,
                    "requestsPerSecond": 5.0,
                    "overflowMode": "reject",
                    "apiTarget": "none",
                    "serviceTimeMs": 50,
                    "jitterMs": 20,
                    "failureRate": 0.0,
                    "workerConcurrency": 50
                  }
                }
                """.trimIndent()

            val response =
                client.patch("/simulations/${handle.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(patchBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val stored = registry.get(handle.id)!!
            assertEquals(OverflowMode.REJECT, stored.config.overflowMode)
        }

    @Test
    fun `PATCH simulations applies apiTarget change from running handle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()

            val patchBody =
                """
                {
                  "config": {
                    "limiterType": "bursty",
                    "permits": 5,
                    "perSeconds": 1.0,
                    "warmupSeconds": 0,
                    "requestsPerSecond": 5.0,
                    "overflowMode": "queue",
                    "apiTarget": "jsonplaceholder",
                    "serviceTimeMs": 50,
                    "jitterMs": 20,
                    "failureRate": 0.0,
                    "workerConcurrency": 50
                  }
                }
                """.trimIndent()

            val response =
                client.patch("/simulations/${handle.id}") {
                    contentType(ContentType.Application.Json)
                    setBody(patchBody)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val stored = registry.get(handle.id)!!
            assertEquals(ApiTarget.JSONPLACEHOLDER, stored.config.apiTarget)
        }

    @Test
    fun `DELETE simulations prepends a Stopped entry into the status log`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }

            val created = client.submitForm(url = "/simulations", formParameters = validConfigParams())
            assertEquals(HttpStatusCode.OK, created.status)
            val handle = registry.list().single()

            val response = client.delete("/simulations/${handle.id}")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()

            assertTrue("selector #$STATUS_LOG_ID" in body, "DELETE response must target status log list")
            assertTrue("mergeMode prepend" in body, "DELETE response must prepend the Stopped entry")
            assertTrue("Stopped" in body, "DELETE response must include the Stopped message")
        }

    @Test
    fun `PATCH simulations returns 404 for missing handle`() =
        testApplication {
            val registry = SimulationRegistry()
            application { module(registry) }
            val response =
                client.submitForm(
                    url = "/simulations/does-not-exist",
                    formParameters = validConfigParams(),
                ) { method = HttpMethod.Patch }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
