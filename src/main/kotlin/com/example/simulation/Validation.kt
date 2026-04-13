package com.example.simulation

data class FieldError(val field: String, val message: String)

sealed class ValidationResult {
    data class Valid(val config: SimulationConfig) : ValidationResult()
    data class Invalid(
        val fieldErrors: List<FieldError>,
        val globalErrors: List<String> = emptyList(),
    ) : ValidationResult()
}

data class RawSimulationConfig(
    val limiterType: String? = null,
    val permits: String? = null,
    val perSeconds: String? = null,
    val warmupSeconds: String? = null,
    val secondaryPermits: String? = null,
    val secondaryPerSeconds: String? = null,
    val requestsPerSecond: String? = null,
    val overflowMode: String? = null,
    val apiTarget: String? = null,
    val serviceTimeMs: String? = null,
    val jitterMs: String? = null,
    val failureRate: String? = null,
    val workerConcurrency: String? = null,
)

object Validator {
    fun validate(raw: RawSimulationConfig): ValidationResult {
        val errors = mutableListOf<FieldError>()

        val limiterType = LimiterType.fromWire(raw.limiterType)
        if (limiterType == null) {
            errors += FieldError("limiterType", "must be one of bursty, smooth, composite")
        }
        val overflowMode = OverflowMode.fromWire(raw.overflowMode)
        if (overflowMode == null) {
            errors += FieldError("overflowMode", "must be one of queue, reject")
        }
        val apiTarget = ApiTarget.fromWire(raw.apiTarget)
        if (apiTarget == null) {
            errors += FieldError("apiTarget", "must be one of none, catfact, jsonplaceholder")
        }

        val permits = requireInt(raw.permits, "permits", errors) { it > 0 }
        val perSeconds = requireDouble(raw.perSeconds, "perSeconds", errors) { it > 0.0 }
        val warmupSeconds = requireDouble(raw.warmupSeconds, "warmupSeconds", errors) { it >= 0.0 }
        val requestsPerSecond =
            requireDouble(raw.requestsPerSecond, "requestsPerSecond", errors) { it >= 0.0 }
        val serviceTimeMs = requireLong(raw.serviceTimeMs, "serviceTimeMs", errors) { it >= 0 }
        val jitterMs = requireLong(raw.jitterMs, "jitterMs", errors) { it >= 0 }
        val failureRate =
            requireDouble(raw.failureRate, "failureRate", errors) { it in 0.0..1.0 }
        val workerConcurrency =
            requireInt(raw.workerConcurrency, "workerConcurrency", errors) { it > 0 }

        var secondaryPermits: Int? = null
        var secondaryPerSeconds: Double? = null
        if (limiterType == LimiterType.COMPOSITE) {
            secondaryPermits = requireInt(raw.secondaryPermits, "secondaryPermits", errors) { it > 0 }
            secondaryPerSeconds =
                requireDouble(raw.secondaryPerSeconds, "secondaryPerSeconds", errors) { it > 0.0 }
        }

        if (errors.isNotEmpty()) return ValidationResult.Invalid(errors.toList())

        return ValidationResult.Valid(
            SimulationConfig(
                limiterType = limiterType!!,
                permits = permits!!,
                perSeconds = perSeconds!!,
                warmupSeconds = warmupSeconds!!,
                secondaryPermits = secondaryPermits,
                secondaryPerSeconds = secondaryPerSeconds,
                requestsPerSecond = requestsPerSecond!!,
                overflowMode = overflowMode!!,
                apiTarget = apiTarget!!,
                serviceTimeMs = serviceTimeMs!!,
                jitterMs = jitterMs!!,
                failureRate = failureRate!!,
                workerConcurrency = workerConcurrency!!,
            ),
        )
    }

    private inline fun requireInt(
        raw: String?,
        field: String,
        errors: MutableList<FieldError>,
        predicate: (Int) -> Boolean,
    ): Int? {
        if (raw.isNullOrBlank()) {
            errors += FieldError(field, "is required")
            return null
        }
        val parsed = raw.toIntOrNull()
        if (parsed == null) {
            errors += FieldError(field, "must be an integer")
            return null
        }
        if (!predicate(parsed)) {
            errors += FieldError(field, "is out of range")
            return null
        }
        return parsed
    }

    private inline fun requireLong(
        raw: String?,
        field: String,
        errors: MutableList<FieldError>,
        predicate: (Long) -> Boolean,
    ): Long? {
        if (raw.isNullOrBlank()) {
            errors += FieldError(field, "is required")
            return null
        }
        val parsed = raw.toLongOrNull()
        if (parsed == null) {
            errors += FieldError(field, "must be an integer")
            return null
        }
        if (!predicate(parsed)) {
            errors += FieldError(field, "is out of range")
            return null
        }
        return parsed
    }

    private inline fun requireDouble(
        raw: String?,
        field: String,
        errors: MutableList<FieldError>,
        predicate: (Double) -> Boolean,
    ): Double? {
        if (raw.isNullOrBlank()) {
            errors += FieldError(field, "is required")
            return null
        }
        val parsed = raw.toDoubleOrNull()
        if (parsed == null || parsed.isNaN() || parsed.isInfinite()) {
            errors += FieldError(field, "must be a number")
            return null
        }
        if (!predicate(parsed)) {
            errors += FieldError(field, "is out of range")
            return null
        }
        return parsed
    }
}
