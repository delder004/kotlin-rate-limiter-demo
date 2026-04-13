package com.example.simulation

import ratelimiter.BurstyRateLimiter
import ratelimiter.CompositeRateLimiter
import ratelimiter.Permit
import ratelimiter.RateLimiter
import ratelimiter.RefundableRateLimiter
import ratelimiter.SmoothRateLimiter
import kotlin.time.Duration.Companion.milliseconds

sealed class EnginePermit {
    object Granted : EnginePermit()

    data class Denied(val retryAfterMs: Long) : EnginePermit()
}

interface EngineLimiter {
    suspend fun acquire()

    fun tryAcquire(): EnginePermit
}

object LimiterFactory {
    fun create(config: SimulationConfig): EngineLimiter = RateLimiterAdapter(buildRateLimiter(config))

    internal fun buildRateLimiter(config: SimulationConfig): RateLimiter {
        val perDuration = (config.perSeconds * 1000).toLong().milliseconds
        return when (config.limiterType) {
            LimiterType.BURSTY ->
                BurstyRateLimiter(
                    permits = config.permits,
                    per = perDuration,
                )
            LimiterType.SMOOTH ->
                SmoothRateLimiter(
                    permits = config.permits,
                    per = perDuration,
                    warmup = (config.warmupSeconds * 1000).toLong().milliseconds,
                )
            LimiterType.COMPOSITE -> {
                require(config.compositeChildren.isNotEmpty()) {
                    "composite config requires at least one child limiter"
                }
                val childLimiters =
                    config.compositeChildren.map { child ->
                        buildChildLimiter(child)
                    }
                CompositeRateLimiter(*childLimiters.toTypedArray())
            }
        }
    }

    private fun buildChildLimiter(child: CompositeChild): RefundableRateLimiter {
        val childPer = (child.perSeconds * 1000).toLong().milliseconds
        return when (child.limiterType) {
            LimiterType.BURSTY ->
                BurstyRateLimiter(
                    permits = child.permits,
                    per = childPer,
                )
            LimiterType.SMOOTH ->
                SmoothRateLimiter(
                    permits = child.permits,
                    per = childPer,
                    warmup = (child.warmupSeconds * 1000).toLong().milliseconds,
                )
            LimiterType.COMPOSITE ->
                throw IllegalArgumentException("nested composite children are not supported")
        }
    }
}

private class RateLimiterAdapter(private val inner: RateLimiter) : EngineLimiter {
    override suspend fun acquire() {
        inner.acquire()
    }

    override fun tryAcquire(): EnginePermit =
        when (val permit = inner.tryAcquire()) {
            is Permit.Granted -> EnginePermit.Granted
            is Permit.Denied -> EnginePermit.Denied(retryAfterMs = permit.retryAfter.inWholeMilliseconds)
            else -> EnginePermit.Granted
        }
}
