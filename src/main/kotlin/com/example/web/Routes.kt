package com.example.web

import com.example.simulation.MAX_COMPOSITE_CHILDREN
import com.example.simulation.RawCompositeChild
import com.example.simulation.RawSimulationConfig
import com.example.simulation.SimulationRegistry
import com.example.simulation.ValidationResult
import com.example.simulation.Validator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Route.registerRoutes(registry: SimulationRegistry) {
    get("/") {
        call.respondHtml { renderPageShell() }
    }

    post("/simulations") {
        val raw = call.receiveRawConfig()
        when (val result = Validator.validate(raw)) {
            is ValidationResult.Invalid -> {
                call.respondDatastar(
                    DatastarResponse()
                        .patchFormErrors(result.fieldErrors, result.globalErrors),
                )
            }
            is ValidationResult.Valid -> {
                val handle = registry.create(result.config)
                call.respondDatastar(
                    DatastarResponse()
                        .clearFormErrors()
                        .patchSimLifecycle(handle.id, handle.status.wire, handle.isRunning)
                        .patchLifecycleControls(handle)
                        .patchStreamAnchor(handle)
                        .prependStatusLogEntry("Started"),
                )
            }
        }
    }

    get("/simulations/{id}/stream") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "missing id")
            return@get
        }
        val handle = registry.get(id)
        if (handle == null) {
            call.respond(HttpStatusCode.NotFound, "simulation $id not found")
            return@get
        }

        call.response.cacheControl(CacheControl.NoCache(null))
        call.response.header("X-Accel-Buffering", "no")

        call.respondBytesWriter(
            contentType = ContentType.parse("text/event-stream"),
            status = HttpStatusCode.OK,
        ) {
            val subscriber = handle.attachSubscriber()
            try {
                for (event in StreamEventMapper.initialStateEvents(handle)) {
                    writeStringUtf8(event.render())
                }
                flush()

                // If the handle is already stopped, deliver the final state and exit.
                if (!handle.isRunning) {
                    return@respondBytesWriter
                }

                for (event in subscriber.events) {
                    for (dsEvent in StreamEventMapper.toDatastarEvents(event)) {
                        writeStringUtf8(dsEvent.render())
                    }
                    flush()
                }
            } catch (_: CancellationException) {
                // client disconnected
            } finally {
                handle.detachSubscriber(subscriber)
            }
        }
    }

    patch("/simulations/{id}") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "missing id")
            return@patch
        }
        if (registry.get(id) == null) {
            call.respond(HttpStatusCode.NotFound, "simulation $id not found")
            return@patch
        }
        val raw = call.receiveRawConfig()
        when (val result = Validator.validate(raw)) {
            is ValidationResult.Invalid -> {
                call.respondDatastar(
                    DatastarResponse()
                        .patchFormErrors(result.fieldErrors, result.globalErrors),
                )
            }
            is ValidationResult.Valid -> {
                when (val updateResult = registry.update(id, result.config)) {
                    is SimulationRegistry.UpdateResult.Updated -> {
                        val handle = updateResult.handle
                        // The "Config Updated: ..." status log entry is
                        // published by the registry and streams in via SSE.
                        call.respondDatastar(
                            DatastarResponse()
                                .clearFormErrors()
                                .patchSimLifecycle(handle.id, handle.status.wire, handle.isRunning),
                        )
                    }
                    SimulationRegistry.UpdateResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound, "simulation $id not found")
                    SimulationRegistry.UpdateResult.NotRunning ->
                        call.respond(HttpStatusCode.Conflict, "simulation $id is not running")
                }
            }
        }
    }

    delete("/simulations/{id}") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "missing id")
            return@delete
        }
        val stopped = registry.stop(id)
        if (stopped == null) {
            call.respond(HttpStatusCode.NotFound, "simulation $id not found")
            return@delete
        }
        // Keep sim.id so a subsequent Start click can resume this handle
        // instead of creating a brand new simulation.
        call.respondDatastar(
            DatastarResponse()
                .patchSimLifecycle(stopped.id, "stopped", running = false)
                .patchLifecycleControls(stopped)
                .patchStreamAnchor(null)
                .prependStatusLogEntry("Stopped"),
        )
    }

    post("/simulations/{id}/resume") {
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "missing id")
            return@post
        }
        when (val result = registry.resume(id)) {
            SimulationRegistry.ResumeResult.NotFound ->
                call.respond(HttpStatusCode.NotFound, "simulation $id not found")
            is SimulationRegistry.ResumeResult.AlreadyRunning -> {
                val handle = result.handle
                call.respondDatastar(
                    DatastarResponse()
                        .patchSimLifecycle(handle.id, handle.status.wire, handle.isRunning),
                )
            }
            is SimulationRegistry.ResumeResult.Resumed -> {
                val handle = result.handle
                call.respondDatastar(
                    DatastarResponse()
                        .clearFormErrors()
                        .patchSimLifecycle(handle.id, handle.status.wire, handle.isRunning)
                        .patchLifecycleControls(handle)
                        .patchStreamAnchor(handle)
                        .prependStatusLogEntry("Resumed"),
                )
            }
        }
    }
}

private val lenientJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

private suspend fun ApplicationCall.receiveRawConfig(): RawSimulationConfig {
    val contentType = request.contentType()
    return when {
        contentType.match(ContentType.Application.FormUrlEncoded) -> {
            val params = receiveParameters()
            val count = params["compositeCount"]?.toIntOrNull()?.coerceIn(0, MAX_COMPOSITE_CHILDREN) ?: 0
            val children =
                (0 until count).map { i ->
                    RawCompositeChild(
                        limiterType = params["child${i}Type"],
                        permits = params["child${i}Permits"],
                        perSeconds = params["child${i}PerSeconds"],
                        warmupSeconds = params["child${i}WarmupSeconds"],
                    )
                }
            RawSimulationConfig(
                limiterType = params["limiterType"],
                permits = params["permits"],
                perSeconds = params["perSeconds"],
                warmupSeconds = params["warmupSeconds"],
                compositeCount = params["compositeCount"],
                compositeChildren = children,
                requestsPerSecond = params["requestsPerSecond"],
                overflowMode = params["overflowMode"],
                apiTarget = params["apiTarget"],
                serviceTimeMs = params["serviceTimeMs"],
                jitterMs = params["jitterMs"],
                failureRate = params["failureRate"],
                workerConcurrency = params["workerConcurrency"],
            )
        }
        contentType.match(ContentType.Application.Json) -> {
            val body = receiveText()
            val root = runCatching { lenientJson.parseToJsonElement(body) }.getOrNull()
            val config =
                (root as? JsonObject)?.get("config") as? JsonObject
                    ?: (root as? JsonObject)
                    ?: JsonObject(emptyMap())

            fun field(name: String): String? = config[name]?.asScalarString()
            val count = field("compositeCount")?.toIntOrNull()?.coerceIn(0, MAX_COMPOSITE_CHILDREN) ?: 0
            val children =
                (0 until count).map { i ->
                    RawCompositeChild(
                        limiterType = field("child${i}Type"),
                        permits = field("child${i}Permits"),
                        perSeconds = field("child${i}PerSeconds"),
                        warmupSeconds = field("child${i}WarmupSeconds"),
                    )
                }
            RawSimulationConfig(
                limiterType = field("limiterType"),
                permits = field("permits"),
                perSeconds = field("perSeconds"),
                warmupSeconds = field("warmupSeconds"),
                compositeCount = field("compositeCount"),
                compositeChildren = children,
                requestsPerSecond = field("requestsPerSecond"),
                overflowMode = field("overflowMode"),
                apiTarget = field("apiTarget"),
                serviceTimeMs = field("serviceTimeMs"),
                jitterMs = field("jitterMs"),
                failureRate = field("failureRate"),
                workerConcurrency = field("workerConcurrency"),
            )
        }
        else -> {
            val q = request.queryParameters
            val count = q["compositeCount"]?.toIntOrNull()?.coerceIn(0, MAX_COMPOSITE_CHILDREN) ?: 0
            val children =
                (0 until count).map { i ->
                    RawCompositeChild(
                        limiterType = q["child${i}Type"],
                        permits = q["child${i}Permits"],
                        perSeconds = q["child${i}PerSeconds"],
                        warmupSeconds = q["child${i}WarmupSeconds"],
                    )
                }
            RawSimulationConfig(
                limiterType = q["limiterType"],
                permits = q["permits"],
                perSeconds = q["perSeconds"],
                warmupSeconds = q["warmupSeconds"],
                compositeCount = q["compositeCount"],
                compositeChildren = children,
                requestsPerSecond = q["requestsPerSecond"],
                overflowMode = q["overflowMode"],
                apiTarget = q["apiTarget"],
                serviceTimeMs = q["serviceTimeMs"],
                jitterMs = q["jitterMs"],
                failureRate = q["failureRate"],
                workerConcurrency = q["workerConcurrency"],
            )
        }
    }
}

private fun JsonElement.asScalarString(): String? {
    if (this is JsonNull) return null
    val prim = this as? JsonPrimitive ?: return null
    return prim.content
}
