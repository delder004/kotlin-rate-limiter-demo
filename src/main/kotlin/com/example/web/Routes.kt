package com.example.web

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
                        .patchStreamAnchor(handle),
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
                        call.respondDatastar(
                            DatastarResponse()
                                .clearFormErrors()
                                .patchSimLifecycle(handle.id, handle.status.wire, handle.isRunning)
                                .mergeFragment(renderWarningFragment("Config updated")),
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
        call.respondDatastar(
            DatastarResponse()
                .patchSimLifecycle(null, "idle", running = false)
                .patchLifecycleControls(null)
                .patchStreamAnchor(null)
                .mergeFragment(renderEmptyWarningsFragment()),
        )
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

private suspend fun ApplicationCall.receiveRawConfig(): RawSimulationConfig {
    val contentType = request.contentType()
    return when {
        contentType.match(ContentType.Application.FormUrlEncoded) -> {
            val params = receiveParameters()
            RawSimulationConfig(
                limiterType = params["limiterType"],
                permits = params["permits"],
                perSeconds = params["perSeconds"],
                warmupSeconds = params["warmupSeconds"],
                secondaryPermits = params["secondaryPermits"],
                secondaryPerSeconds = params["secondaryPerSeconds"],
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
            val config = (root as? JsonObject)?.get("config") as? JsonObject
                ?: (root as? JsonObject)
                ?: JsonObject(emptyMap())
            fun field(name: String): String? = config[name]?.asScalarString()
            RawSimulationConfig(
                limiterType = field("limiterType"),
                permits = field("permits"),
                perSeconds = field("perSeconds"),
                warmupSeconds = field("warmupSeconds"),
                secondaryPermits = field("secondaryPermits"),
                secondaryPerSeconds = field("secondaryPerSeconds"),
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
            RawSimulationConfig(
                limiterType = q["limiterType"],
                permits = q["permits"],
                perSeconds = q["perSeconds"],
                warmupSeconds = q["warmupSeconds"],
                secondaryPermits = q["secondaryPermits"],
                secondaryPerSeconds = q["secondaryPerSeconds"],
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
