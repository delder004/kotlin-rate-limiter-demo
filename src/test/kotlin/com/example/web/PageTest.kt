package com.example.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertTrue

class PageTest {
    private val rendered: String = createHTML().html { renderPageShell() }

    @Test
    fun `shell contains page title`() {
        assertTrue("Kotlin Rate Limiter Demo" in rendered, "title missing")
    }

    @Test
    fun `shell contains page-root with initial signals`() {
        assertTrue("id=\"page-root\"" in rendered)
        assertTrue("data-signals" in rendered)
        assertTrue("&quot;step&quot;: 1" in rendered, "initial ui.step missing")
        assertTrue("idle" in rendered)
    }

    @Test
    fun `shell includes inline stylesheet for wizard layout`() {
        assertTrue("<style>" in rendered, "style tag missing")
        assertTrue(".wizard-step" in rendered, "wizard-step styles missing")
        assertTrue(".limiter-buttons" in rendered, "limiter-buttons styles missing")
        assertTrue("input[type=\"range\"]" in rendered, "range input styles missing")
    }

    @Test
    fun `step 1 offers a limiter choice with button options`() {
        val step =
            rendered
                .substringAfter("id=\"step-limiter\"")
                .substringBefore("id=\"step-permits\"")
        assertTrue("Choose your limiter:" in step, "step heading missing")
        for (value in listOf("bursty", "smooth", "composite")) {
            assertTrue("id=\"limiter-$value\"" in step, "missing limiter button $value")
            assertTrue(
                "\$config.limiterType = '$value'" in step,
                "limiter button $value should set limiterType",
            )
        }
        assertTrue(
            "\$ui.step = Math.max(\$ui.step, 2)" in step,
            "limiter button should advance wizard step",
        )
    }

    @Test
    fun `step 2 reveals permits slider after limiter is chosen`() {
        val step =
            rendered
                .substringAfter("id=\"step-permits\"")
                .substringBefore("id=\"step-traffic\"")
        assertTrue("Permits:" in step, "step heading missing")
        assertTrue(
            "\$ui.step &gt;= 2" in step && "limiterType !== 'composite'" in step,
            "step-permits should be gated on step>=2 and hidden for composite",
        )
        assertTrue("id=\"input-permits\"" in step, "permits slider missing")
        assertTrue("type=\"range\"" in step, "permits control should be a range slider")
        // Server validation requires permits > 0 for non-composite limiters, so
        // the range slider is bounded at 1 and the initial signal value of 0
        // keeps the wizard at step 2 until the user actually moves the control.
        assertTrue(
            "id=\"input-permits\" type=\"range\" min=\"1\" max=\"500\"" in step,
            "permits range slider should have min=1",
        )
        assertTrue("data-bind=\"config.permits\"" in step, "permits slider should bind")
        assertTrue(
            "\$config.permits &gt; 0" in step &&
                "\$ui.step = Math.max(\$ui.step, 3)" in step,
            "step-permits should advance only once permits > 0",
        )
    }

    @Test
    fun `step 3 reveals traffic slider after permits`() {
        val step =
            rendered
                .substringAfter("id=\"step-traffic\"")
                .substringBefore("id=\"step-start\"")
        assertTrue("Requests per sec:" in step, "step heading missing")
        assertTrue("data-show=\"\$ui.step &gt;= 3\"" in step, "step-traffic should be gated")
        assertTrue("id=\"input-requestsPerSecond\"" in step, "rps slider missing")
        assertTrue("type=\"range\"" in step, "rps control should be a range slider")
        assertTrue("min=\"0\"" in step, "rps slider should start at 0")
        assertTrue("data-bind=\"config.requestsPerSecond\"" in step, "rps slider should bind")
        assertTrue(
            "\$config.requestsPerSecond &gt; 0" in step &&
                "\$ui.step = Math.max(\$ui.step, 4)" in step,
            "step-traffic should advance only once requestsPerSecond > 0",
        )
    }

    @Test
    fun `step 3 exposes advanced traffic sliders for service time jitter failure rate and worker concurrency`() {
        val step =
            rendered
                .substringAfter("id=\"step-traffic\"")
                .substringBefore("id=\"step-start\"")
        assertTrue("traffic-advanced" in step, "advanced disclosure wrapper missing")
        assertTrue("<summary>Advanced</summary>" in step, "advanced summary label missing")
        val fields =
            listOf(
                "serviceTimeMs" to "config.serviceTimeMs",
                "jitterMs" to "config.jitterMs",
                "failureRate" to "config.failureRate",
                "workerConcurrency" to "config.workerConcurrency",
            )
        for ((field, signal) in fields) {
            assertTrue("id=\"input-$field\"" in step, "$field slider missing")
            assertTrue("data-bind=\"$signal\"" in step, "$field bind missing")
            assertTrue(
                "id=\"${fieldErrorId(field)}\"" in step,
                "$field error slot missing",
            )
        }
    }

    @Test
    fun `step 4 reveals start button`() {
        val step =
            rendered
                .substringAfter("id=\"step-start\"")
                .substringBefore("id=\"run-panels\"")
        assertTrue("data-show=\"\$ui.step &gt;= 4\"" in step, "step-start should be gated")
        assertTrue("id=\"$LIFECYCLE_CONTROLS_ID\"" in step, "lifecycle-controls slot missing")
        assertTrue("id=\"start-button\"" in step, "start button missing")
        assertTrue("id=\"stop-button\"" in step, "stop button missing")
    }

    @Test
    fun `run panels become visible once start is clicked and stay visible after stop`() {
        val runPanels =
            rendered
                .substringAfter("id=\"run-panels\"")
        assertTrue(
            "data-show=\"\$ui.step &gt;= 5\"" in runPanels,
            "run-panels should be gated on ui.step >= 5 so they persist after Stop",
        )
        assertTrue("id=\"stats-panel\"" in runPanels)
        assertTrue("id=\"chart-panel\"" in runPanels)
        assertTrue("id=\"log-panel\"" in runPanels)
        assertTrue("id=\"status-log-panel\"" in runPanels)
    }

    @Test
    fun `stats panel renders all stats fields`() {
        for (field in listOf(
            "stats.queued",
            "stats.inFlight",
            "stats.admitted",
            "stats.completed",
            "stats.denied",
            "stats.droppedIncoming",
            "stats.droppedOutgoing",
            "stats.acceptRate",
            "stats.rejectRate",
            "stats.avgLatencyMs",
            "stats.p50LatencyMs",
            "stats.p95LatencyMs",
        )) {
            assertTrue(field in rendered, "stats field $field missing")
        }
    }

    @Test
    fun `run panels contain chart mount stream anchor log slot and status log slot`() {
        assertTrue("id=\"$CHART_MOUNT_ID\"" in rendered)
        assertTrue("id=\"$STREAM_ANCHOR_ID\"" in rendered)
        assertTrue("id=\"$LOG_LIST_ID\"" in rendered)
        assertTrue("id=\"$STATUS_LOG_ID\"" in rendered)
    }

    @Test
    fun `status log panel renders above response log panel`() {
        val runPanels = rendered.substringAfter("id=\"run-panels\"")
        val statusIdx = runPanels.indexOf("id=\"status-log-panel\"")
        val logIdx = runPanels.indexOf("id=\"log-panel\"")
        assertTrue(statusIdx > 0, "status-log-panel should exist")
        assertTrue(logIdx > 0, "log-panel should exist")
        assertTrue(statusIdx < logIdx, "status-log-panel should render before log-panel")
        assertTrue("Status Log" in runPanels, "status log heading missing")
    }

    @Test
    fun `form and stream error slots remain available for validation patches`() {
        assertTrue("id=\"errors-form\"" in rendered)
        assertTrue("id=\"errors-stream\"" in rendered)
    }

    @Test
    fun `exposed fields have per-field error slots`() {
        for (field in listOf("limiterType", "permits", "requestsPerSecond")) {
            assertTrue(
                "id=\"${fieldErrorId(field)}\"" in rendered,
                "missing field error slot for $field",
            )
        }
    }

    @Test
    fun `status badge is present inside run panels`() {
        assertTrue("id=\"$STATUS_BADGE_ID\"" in rendered)
    }

    @Test
    fun `shell includes datastar script`() {
        assertTrue("datastar" in rendered, "datastar script missing")
    }

    @Test
    fun `slider value fields expose editable number inputs with aria labels`() {
        val numberFields =
            listOf(
                "input-permits-value" to "Permits value",
                "input-warmupSeconds-value" to "Warmup seconds value",
                "input-requestsPerSecond-value" to "Requests per second value",
                "input-serviceTimeMs-value" to "Service time milliseconds value",
                "input-jitterMs-value" to "Jitter milliseconds value",
                "input-failureRate-value" to "Failure rate percent value",
                "input-workerConcurrency-value" to "Worker concurrency value",
                "input-child0Permits-value" to "Limiter 1 permits value",
                "input-child0WarmupSeconds-value" to "Limiter 1 warmup seconds value",
            )
        for ((id, label) in numberFields) {
            assertTrue("id=\"$id\"" in rendered, "$id missing")
            assertTrue(
                "aria-label=\"$label\"" in rendered,
                "$id missing aria-label '$label'",
            )
        }
        // Every slider-value-input should be type=number so typing bypasses the slider step.
        assertTrue(
            "class=\"slider-value-input\" id=\"input-permits-value\" type=\"number\"" in rendered,
            "permits value input should be type=number",
        )
    }

    @Test
    fun `slider value inputs clamp and coerce on change`() {
        // Integer field: coercion rounds and clamps to min..max. serviceTimeMs
        // is the integer field with an inclusive 0 lower bound (permits is
        // special-cased to >= 1 because the server rejects 0).
        assertTrue(
            "\$config.serviceTimeMs = Math.min(500, Math.max(0, Math.round(" +
                "Number(\$config.serviceTimeMs) || 0)))" in rendered,
            "serviceTimeMs coercion missing",
        )
        // Float field: coercion clamps without rounding
        assertTrue(
            "\$config.warmupSeconds = Math.min(30, Math.max(0, Number(\$config.warmupSeconds) || 0))" in
                rendered,
            "warmupSeconds coercion missing",
        )
        // Worker concurrency has non-zero min
        assertTrue(
            "\$config.workerConcurrency = Math.min(200, Math.max(1, Math.round(" +
                "Number(\$config.workerConcurrency) || 0)))" in rendered,
            "workerConcurrency coercion missing",
        )
    }

    @Test
    fun `permits value input can never coerce to zero`() {
        // Regression: the server rejects permits = 0 for non-composite limiters,
        // so the value input's coercion must clamp to the server-accepted range
        // [1, 500] instead of [0, 500]. Blank or sub-1 entries round up to 1.
        val step =
            rendered
                .substringAfter("id=\"step-permits\"")
                .substringBefore("id=\"step-traffic\"")
        assertTrue(
            "\$config.permits = Math.min(500, Math.max(1, Math.round(Number(\$config.permits) || 0)))" in
                step,
            "permits coercion must clamp to >= 1",
        )
        // The helper emits min=1 on the number input attribute as well.
        assertTrue(
            "id=\"input-permits-value\" type=\"number\" min=\"1\"" in step,
            "permits value input should have min=1",
        )
    }

    @Test
    fun `permits value input coercion absorbs sub-integer entry that would otherwise advance the wizard`() {
        // Regression for the 0.4 -> step-3 -> blur -> 0 race. Step advance fires
        // on data-on-input (raw signal), so typing 0.4 legitimately advances the
        // wizard to step 3. The later data-on-change coercion must round to an
        // integer AND clamp to >= 1 so the wizard is never left at a later step
        // with a permits value the server rejects.
        val step =
            rendered
                .substringAfter("id=\"step-permits\"")
                .substringBefore("id=\"step-traffic\"")
        // Step advance gate on data-on-input stays permissive.
        assertTrue(
            "data-on-input=\"\$config.permits &gt; 0 &amp;&amp; " +
                "(\$ui.step = Math.max(\$ui.step, 3))\"" in step,
            "permits value input should advance on input",
        )
        // Coercion wraps Math.round in Math.max(1, ...) so sub-1 rounds to 1.
        assertTrue(
            "Math.max(1, Math.round(Number(\$config.permits) || 0))" in step,
            "permits coercion must floor at 1 after rounding",
        )
    }

    @Test
    fun `failure rate input stores 0 to 1 while displaying percent`() {
        val step =
            rendered
                .substringAfter("id=\"step-traffic\"")
                .substringBefore("id=\"step-start\"")
        // The percent input binds to the ui helper signal, not config.
        assertTrue("data-bind=\"ui.failureRatePct\"" in step, "percent input should bind ui signal")
        assertTrue("min=\"0\"" in step && "max=\"100\"" in step)
        // Typing into the percent input syncs config.failureRate back to 0..1.
        assertTrue(
            "\$config.failureRate = Math.min(1, Math.max(0, (Number(\$ui.failureRatePct) || 0) / 100))" in
                step,
            "percent -> config sync missing",
        )
        // Dragging the range slider keeps the percent input up to date.
        assertTrue(
            "\$ui.failureRatePct = Math.round(\$config.failureRate * 100)" in step,
            "config -> percent sync missing",
        )
        // The initial signals payload seeds the helper signal.
        assertTrue(
            "&quot;failureRatePct&quot;: 0" in rendered,
            "initial ui.failureRatePct missing from signals",
        )
    }
}
