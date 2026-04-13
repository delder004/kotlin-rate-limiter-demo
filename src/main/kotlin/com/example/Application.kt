package com.example

import com.example.simulation.CoroutineSimulationEngine
import com.example.simulation.SimulationRegistry
import com.example.web.registerRoutes
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() {
    val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engine = CoroutineSimulationEngine(scope = engineScope)
    val registry = SimulationRegistry(engine = engine)
    embeddedServer(Netty, port = 8080) { module(registry) }.start(wait = true)
}

fun Application.module(registry: SimulationRegistry = SimulationRegistry()) {
    routing {
        registerRoutes(registry)
    }
}
