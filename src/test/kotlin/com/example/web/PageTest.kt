package com.example.web

import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertTrue

class PageTest {
    private val rendered: String = createHTML().html { renderPageShell() }

    @Test
    fun `shell contains page title`() {
        assertTrue("Rate Limiter Sandbox" in rendered, "title missing")
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
        val step = rendered
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
        val step = rendered
            .substringAfter("id=\"step-permits\"")
            .substringBefore("id=\"step-traffic\"")
        assertTrue("Permits:" in step, "step heading missing")
        assertTrue(
            "\$ui.step &gt;= 2" in step && "limiterType !== 'composite'" in step,
            "step-permits should be gated on step>=2 and hidden for composite",
        )
        assertTrue("id=\"input-permits\"" in step, "permits slider missing")
        assertTrue("type=\"range\"" in step, "permits control should be a range slider")
        assertTrue("min=\"0\"" in step, "permits slider should start at 0")
        assertTrue("data-bind=\"config.permits\"" in step, "permits slider should bind")
        assertTrue(
            "\$config.permits &gt; 0" in step &&
                "\$ui.step = Math.max(\$ui.step, 3)" in step,
            "step-permits should advance only once permits > 0",
        )
    }

    @Test
    fun `step 3 reveals traffic slider after permits`() {
        val step = rendered
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
    fun `step 4 reveals start button`() {
        val step = rendered
            .substringAfter("id=\"step-start\"")
            .substringBefore("id=\"run-panels\"")
        assertTrue("data-show=\"\$ui.step &gt;= 4\"" in step, "step-start should be gated")
        assertTrue("id=\"$LIFECYCLE_CONTROLS_ID\"" in step, "lifecycle-controls slot missing")
        assertTrue("id=\"start-button\"" in step, "start button missing")
        assertTrue("id=\"stop-button\"" in step, "stop button missing")
    }

    @Test
    fun `run panels become visible once start is clicked and stay visible after stop`() {
        val runPanels = rendered
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
}
