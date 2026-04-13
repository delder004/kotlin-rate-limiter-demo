package com.example.simulation

enum class LimiterType(val wire: String) {
    BURSTY("bursty"),
    SMOOTH("smooth"),
    COMPOSITE("composite");

    companion object {
        fun fromWire(value: String?): LimiterType? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

enum class OverflowMode(val wire: String) {
    QUEUE("queue"),
    REJECT("reject");

    companion object {
        fun fromWire(value: String?): OverflowMode? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

enum class ApiTarget(val wire: String) {
    NONE("none"),
    CATFACT("catfact"),
    JSONPLACEHOLDER("jsonplaceholder");

    companion object {
        fun fromWire(value: String?): ApiTarget? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

data class SimulationConfig(
    val limiterType: LimiterType,
    val permits: Int,
    val perSeconds: Double,
    val warmupSeconds: Double,
    val secondaryPermits: Int?,
    val secondaryPerSeconds: Double?,
    val requestsPerSecond: Double,
    val overflowMode: OverflowMode,
    val apiTarget: ApiTarget,
    val serviceTimeMs: Long,
    val jitterMs: Long,
    val failureRate: Double,
    val workerConcurrency: Int,
)
