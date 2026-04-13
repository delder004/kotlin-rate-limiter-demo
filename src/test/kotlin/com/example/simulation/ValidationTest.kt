package com.example.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidationTest {
    private fun baseRaw(
        limiterType: String? = "bursty",
        permits: String? = "5",
        perSeconds: String? = "1.0",
        warmupSeconds: String? = "0",
        secondaryPermits: String? = null,
        secondaryPerSeconds: String? = null,
        requestsPerSecond: String? = "5.0",
        overflowMode: String? = "queue",
        apiTarget: String? = "none",
        serviceTimeMs: String? = "50",
        jitterMs: String? = "20",
        failureRate: String? = "0.0",
        workerConcurrency: String? = "50",
    ) = RawSimulationConfig(
        limiterType, permits, perSeconds, warmupSeconds,
        secondaryPermits, secondaryPerSeconds,
        requestsPerSecond, overflowMode, apiTarget,
        serviceTimeMs, jitterMs, failureRate, workerConcurrency,
    )

    private fun assertValid(raw: RawSimulationConfig): SimulationConfig {
        val result = Validator.validate(raw)
        assertIs<ValidationResult.Valid>(result, "expected valid, got $result")
        return result.config
    }

    private fun assertInvalidOnField(raw: RawSimulationConfig, field: String) {
        val result = Validator.validate(raw)
        assertIs<ValidationResult.Invalid>(result, "expected invalid for $field")
        assertTrue(
            result.fieldErrors.any { it.field == field },
            "expected error on $field, got ${result.fieldErrors}",
        )
    }

    @Test
    fun `valid bursty config`() {
        val config = assertValid(baseRaw())
        assertEquals(LimiterType.BURSTY, config.limiterType)
        assertEquals(5, config.permits)
        assertEquals(OverflowMode.QUEUE, config.overflowMode)
        assertEquals(ApiTarget.NONE, config.apiTarget)
    }

    @Test
    fun `valid smooth config`() {
        val config = assertValid(baseRaw(limiterType = "smooth", warmupSeconds = "2.5"))
        assertEquals(LimiterType.SMOOTH, config.limiterType)
        assertEquals(2.5, config.warmupSeconds)
    }

    @Test
    fun `valid composite config`() {
        val config = assertValid(
            baseRaw(
                limiterType = "composite",
                secondaryPermits = "10",
                secondaryPerSeconds = "60.0",
            ),
        )
        assertEquals(LimiterType.COMPOSITE, config.limiterType)
        assertEquals(10, config.secondaryPermits)
        assertEquals(60.0, config.secondaryPerSeconds)
    }

    @Test
    fun `zero requests per second is allowed`() {
        val config = assertValid(baseRaw(requestsPerSecond = "0"))
        assertEquals(0.0, config.requestsPerSecond)
    }

    @Test
    fun `zero permits rejected`() {
        assertInvalidOnField(baseRaw(permits = "0"), "permits")
    }

    @Test
    fun `negative permits rejected`() {
        assertInvalidOnField(baseRaw(permits = "-1"), "permits")
    }

    @Test
    fun `non-integer permits rejected`() {
        assertInvalidOnField(baseRaw(permits = "abc"), "permits")
    }

    @Test
    fun `zero perSeconds rejected`() {
        assertInvalidOnField(baseRaw(perSeconds = "0"), "perSeconds")
    }

    @Test
    fun `negative warmup rejected`() {
        assertInvalidOnField(baseRaw(warmupSeconds = "-1"), "warmupSeconds")
    }

    @Test
    fun `negative requestsPerSecond rejected`() {
        assertInvalidOnField(baseRaw(requestsPerSecond = "-0.1"), "requestsPerSecond")
    }

    @Test
    fun `invalid limiter type rejected`() {
        assertInvalidOnField(baseRaw(limiterType = "nonsense"), "limiterType")
    }

    @Test
    fun `invalid overflow mode rejected`() {
        assertInvalidOnField(baseRaw(overflowMode = "drop"), "overflowMode")
    }

    @Test
    fun `invalid api target rejected`() {
        assertInvalidOnField(baseRaw(apiTarget = "twitter"), "apiTarget")
    }

    @Test
    fun `composite missing secondary permits rejected`() {
        assertInvalidOnField(
            baseRaw(limiterType = "composite", secondaryPerSeconds = "10"),
            "secondaryPermits",
        )
    }

    @Test
    fun `composite missing secondary perSeconds rejected`() {
        assertInvalidOnField(
            baseRaw(limiterType = "composite", secondaryPermits = "5"),
            "secondaryPerSeconds",
        )
    }

    @Test
    fun `composite zero secondary permits rejected`() {
        assertInvalidOnField(
            baseRaw(
                limiterType = "composite",
                secondaryPermits = "0",
                secondaryPerSeconds = "10",
            ),
            "secondaryPermits",
        )
    }

    @Test
    fun `failure rate above 1 rejected`() {
        assertInvalidOnField(baseRaw(failureRate = "1.5"), "failureRate")
    }

    @Test
    fun `failure rate below 0 rejected`() {
        assertInvalidOnField(baseRaw(failureRate = "-0.1"), "failureRate")
    }

    @Test
    fun `worker concurrency zero rejected`() {
        assertInvalidOnField(baseRaw(workerConcurrency = "0"), "workerConcurrency")
    }

    @Test
    fun `worker concurrency negative rejected`() {
        assertInvalidOnField(baseRaw(workerConcurrency = "-4"), "workerConcurrency")
    }

    @Test
    fun `negative service time rejected`() {
        assertInvalidOnField(baseRaw(serviceTimeMs = "-1"), "serviceTimeMs")
    }

    @Test
    fun `negative jitter rejected`() {
        assertInvalidOnField(baseRaw(jitterMs = "-1"), "jitterMs")
    }

    @Test
    fun `multiple errors reported together`() {
        val result = Validator.validate(baseRaw(permits = "0", failureRate = "2"))
        assertIs<ValidationResult.Invalid>(result)
        val fields = result.fieldErrors.map { it.field }.toSet()
        assertTrue("permits" in fields && "failureRate" in fields)
    }
}
