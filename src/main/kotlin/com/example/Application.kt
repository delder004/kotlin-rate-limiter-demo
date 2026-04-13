package com.example

import com.example.simulation.CoroutineSimulationEngine
import com.example.simulation.SimulationRegistry
import com.example.web.registerRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() {
    val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engine = CoroutineSimulationEngine(scope = engineScope)
    val registry = SimulationRegistry(engine = engine)
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(registry) }.start(wait = true)
}

fun Application.module(registry: SimulationRegistry = SimulationRegistry()) {
    routing {
        registerRoutes(registry)
    }
}
