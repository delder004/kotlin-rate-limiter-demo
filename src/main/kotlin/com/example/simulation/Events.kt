package com.example.simulation

sealed class SimulationEvent {
    abstract val simulationId: String

    data class Started(
        override val simulationId: String,
        val config: SimulationConfig,
    ) : SimulationEvent()

    data class MetricSample(
        override val simulationId: String,
        val snapshot: MetricsSnapshot,
    ) : SimulationEvent()

    data class ResponseSample(
        override val simulationId: String,
        val entry: LogEntry,
    ) : SimulationEvent()

    data class Warning(
        override val simulationId: String,
        val message: String,
    ) : SimulationEvent()

    data class Stopped(
        override val simulationId: String,
    ) : SimulationEvent()

    data class Failed(
        override val simulationId: String,
        val error: String,
    ) : SimulationEvent()
}
