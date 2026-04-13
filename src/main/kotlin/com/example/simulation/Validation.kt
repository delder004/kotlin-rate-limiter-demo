package com.example.simulation

data class FieldError(val field: String, val message: String)

sealed class ValidationResult {
    data class Valid(val config: SimulationConfig) : ValidationResult()

    data class Invalid(
        val fieldErrors: List<FieldError>,
        val globalErrors: List<String> = emptyList(),
    ) : ValidationResult()
}

const val MAX_COMPOSITE_CHILDREN = 5

data class RawCompositeChild(
    val limiterType: String? = null,
    val permits: String? = null,
    val perSeconds: String? = null,
    val warmupSeconds: String? = null,
)

data class RawSimulationConfig(
    val limiterType: String? = null,
    val permits: String? = null,
    val perSeconds: String? = null,
    val warmupSeconds: String? = null,
    val compositeCount: String? = null,
    val compositeChildren: List<RawCompositeChild> = emptyList(),
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

        // Top-level permits/perSeconds/warmupSeconds are only meaningful for
        // non-composite limiters. For composite, the children carry their own
        // rate config, so we accept whatever the UI sent (usually 0) and fall
        // back to safe defaults.
        val permits: Int?
        val perSeconds: Double?
        val warmupSeconds: Double?
        if (limiterType == LimiterType.COMPOSITE) {
            permits = raw.permits?.toIntOrNull() ?: 1
            perSeconds = raw.perSeconds?.toDoubleOrNull() ?: 1.0
            warmupSeconds = raw.warmupSeconds?.toDoubleOrNull() ?: 0.0
        } else {
            permits = requireInt(raw.permits, "permits", errors) { it > 0 }
            perSeconds = requireDouble(raw.perSeconds, "perSeconds", errors) { it > 0.0 }
            warmupSeconds = requireDouble(raw.warmupSeconds, "warmupSeconds", errors) { it >= 0.0 }
        }
        val requestsPerSecond =
            requireDouble(raw.requestsPerSecond, "requestsPerSecond", errors) { it >= 0.0 }
        val serviceTimeMs = requireLong(raw.serviceTimeMs, "serviceTimeMs", errors) { it >= 0 }
        val jitterMs = requireLong(raw.jitterMs, "jitterMs", errors) { it >= 0 }
        val failureRate =
            requireDouble(raw.failureRate, "failureRate", errors) { it in 0.0..1.0 }
        val workerConcurrency =
            requireInt(raw.workerConcurrency, "workerConcurrency", errors) { it > 0 }

        val compositeChildren = mutableListOf<CompositeChild>()
        if (limiterType == LimiterType.COMPOSITE) {
            val count =
                raw.compositeCount?.toIntOrNull()
                    ?.coerceIn(1, MAX_COMPOSITE_CHILDREN)
                    ?: raw.compositeChildren.size.coerceAtLeast(1)
            if (raw.compositeChildren.size < count) {
                errors += FieldError("compositeChildren", "missing child definitions")
            }
            val childSlice = raw.compositeChildren.take(count)
            for ((index, child) in childSlice.withIndex()) {
                val childType = LimiterType.fromWire(child.limiterType) ?: LimiterType.BURSTY
                if (childType == LimiterType.COMPOSITE) {
                    errors += FieldError("child${index}Type", "must be bursty or smooth")
                    continue
                }
                val childPermits =
                    requireInt(child.permits, "child${index}Permits", errors) { it > 0 }
                val childPerSeconds =
                    requireDouble(child.perSeconds, "child${index}PerSeconds", errors) { it > 0.0 }
                val childWarmup =
                    if (childType == LimiterType.SMOOTH) {
                        requireDouble(child.warmupSeconds, "child${index}WarmupSeconds", errors) { it >= 0.0 }
                    } else {
                        0.0
                    }
                if (childPermits != null && childPerSeconds != null && childWarmup != null) {
                    compositeChildren +=
                        CompositeChild(
                            limiterType = childType,
                            permits = childPermits,
                            perSeconds = childPerSeconds,
                            warmupSeconds = childWarmup,
                        )
                }
            }
            if (errors.none { it.field.startsWith("child") } && compositeChildren.isEmpty()) {
                errors += FieldError("compositeChildren", "at least one child limiter is required")
            }
        }

        if (errors.isNotEmpty()) return ValidationResult.Invalid(errors.toList())

        return ValidationResult.Valid(
            SimulationConfig(
                limiterType = limiterType!!,
                permits = permits!!,
                perSeconds = perSeconds!!,
                warmupSeconds = warmupSeconds!!,
                compositeChildren = compositeChildren.toList(),
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
