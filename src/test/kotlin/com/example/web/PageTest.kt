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
    fun `shell contains hero video with poster and both sources`() {
        assertTrue("id=\"hero\"" in rendered, "hero container missing")
        assertTrue("<video" in rendered, "hero video element missing")
        assertTrue("preload=\"metadata\"" in rendered, "video should use preload=metadata")
        assertTrue("poster=\"/static/hero-poster.jpg\"" in rendered, "poster attribute missing")
        assertTrue("src=\"/static/hero.webm\"" in rendered, "webm source missing")
        assertTrue("src=\"/static/hero.mp4\"" in rendered, "mp4 source missing")
    }

    @Test
    fun `shell contains page-root with initial signals`() {
        assertTrue("id=\"page-root\"" in rendered)
        assertTrue("data-signals" in rendered)
        // Fresh signals seed the UI to a working "under the limit" config so the
        // user can hit Start immediately even without picking a preset.
        assertTrue("&quot;limiterType&quot;: &quot;bursty&quot;" in rendered, "initial limiterType default missing")
        assertTrue("&quot;permits&quot;: 20" in rendered, "initial permits default missing")
        assertTrue("&quot;requestsPerSecond&quot;: 10" in rendered, "initial rps default missing")
        // ui.step signal was removed with the wizard.
        assertTrue("&quot;step&quot;" !in rendered, "ui.step signal should no longer exist")
        assertTrue("idle" in rendered)
    }

    @Test
    fun `shell includes inline stylesheet for layout`() {
        assertTrue("<style>" in rendered, "style tag missing")
        assertTrue(".preset-button" in rendered, "preset-button styles missing")
        assertTrue("#tweak-panel" in rendered, "tweak-panel styles missing")
        assertTrue("input[type=\"range\"]" in rendered, "range input styles missing")
    }

    @Test
    fun `presets panel is the primary entry point with a-ha scenarios`() {
        assertTrue("id=\"presets-panel\"" in rendered, "presets panel missing")
        for (id in listOf("under", "deny", "queue", "burst-drain", "smooth", "composite")) {
            assertTrue("id=\"preset-$id\"" in rendered, "missing preset $id")
        }
        // Each preset button exposes a descriptive label so first-time visitors
        // can scan scenarios without reading the slider rows.
        assertTrue("Exceed the limit · Deny" in rendered, "deny preset label missing")
        assertTrue("Burst then drain" in rendered, "burst-drain preset label missing")
    }

    @Test
    fun `preset click handlers set all config signals and auto-start`() {
        // The "deny" preset should set overflowMode=reject so a first-time click
        // actually produces visible denials — that is the a-ha moment.
        val denyChunk =
            rendered
                .substringAfter("id=\"preset-deny\"")
                .substringBefore("id=\"preset-queue\"")
        assertTrue("\$config.overflowMode = 'reject'" in denyChunk, "deny preset must set overflowMode=reject")
        assertTrue("\$config.requestsPerSecond = 40" in denyChunk, "deny preset rps missing")
        assertTrue("\$config.permits = 10" in denyChunk, "deny preset permits missing")
        // POST on idle click (no running sim), PATCH when already running.
        assertTrue("!\$sim.running &amp;&amp; @post('/simulations')" in denyChunk, "preset should auto-POST when idle")
        assertTrue("\$sim.running &amp;&amp; @patch('/simulations/' + \$sim.id)" in denyChunk, "preset should PATCH when running")
    }

    @Test
    fun `every preset click cancels any pending burst-drain follow-up`() {
        // Regression: clicking Burst-then-drain and then another preset within
        // 5s used to let the old timeout clobber the new scenario's rps. Every
        // preset click must cancel the pending follow-up up front.
        for (id in listOf("under", "deny", "queue", "burst-drain", "smooth", "composite")) {
            val chunk =
                rendered
                    .substringAfter("id=\"preset-$id\"")
                    .substringBefore("</button>")
            assertTrue(
                "window.__cancelFollowUp &amp;&amp; window.__cancelFollowUp()" in chunk,
                "preset $id should call __cancelFollowUp before applying its config",
            )
        }
    }

    @Test
    fun `burst-drain preset schedules a follow-up via window helper`() {
        val chunk =
            rendered
                .substringAfter("id=\"preset-burst-drain\"")
                .substringBefore("id=\"preset-smooth\"")
        assertTrue("window.__scheduleFollowUp(5000, 5)" in chunk, "burst-drain should schedule a follow-up drop to 5 rps")
    }

    @Test
    fun `manual rps slider input cancels pending follow-up`() {
        // Regression: dragging the rps slider during a burst-drain window
        // must cancel the pending drop, otherwise the delayed PATCH clobbers
        // the user's manual tweak.
        val section =
            rendered
                .substringAfter("id=\"tweak-traffic\"")
                .substringBefore("id=\"status-log-panel\"")
        // Both the range input and the number input must cancel on input.
        val rangeBlock =
            section
                .substringAfter("id=\"input-requestsPerSecond\"")
                .substringBefore("</input>")
        assertTrue(
            "window.__cancelFollowUp &amp;&amp; window.__cancelFollowUp()" in rangeBlock,
            "rps range slider should cancel pending follow-up on input",
        )
        val numberBlock =
            section
                .substringAfter("id=\"input-requestsPerSecond-value\"")
                .substringBefore("</span>")
        assertTrue(
            "window.__cancelFollowUp &amp;&amp; window.__cancelFollowUp()" in numberBlock,
            "rps number input should cancel pending follow-up on input",
        )
    }

    @Test
    fun `start and stop buttons cancel pending follow-up`() {
        val startBlock =
            rendered
                .substringAfter("id=\"start-button\"")
                .substringBefore("</button>")
        assertTrue(
            "window.__cancelFollowUp &amp;&amp; window.__cancelFollowUp()" in startBlock,
            "Start button should cancel pending follow-up",
        )
        val stopBlock =
            rendered
                .substringAfter("id=\"stop-button\"")
                .substringBefore("</button>")
        assertTrue(
            "window.__cancelFollowUp &amp;&amp; window.__cancelFollowUp()" in stopBlock,
            "Stop button should cancel pending follow-up",
        )
    }

    @Test
    fun `chart script defines a cancel and effective permits helper`() {
        assertTrue("window.__cancelFollowUp" in rendered, "__cancelFollowUp helper missing")
        assertTrue("window.__effectivePermitsPerSec" in rendered, "__effectivePermitsPerSec helper missing")
    }

    @Test
    fun `chart mount routes permits-per-sec through effective helper`() {
        // For composite presets, the reference line must reflect the slowest
        // child's steady-state rate, not the stale top-level permits/perSeconds.
        // This is what makes the "slower tier kicks in" story visually land.
        assertTrue(
            "window.__effectivePermitsPerSec(" in rendered,
            "chart push must route through __effectivePermitsPerSec",
        )
        assertTrue(
            "\$config.limiterType, \$config.permits, \$config.perSeconds, \$config.compositeCount" in rendered,
            "chart push helper must receive limiter type and composite count",
        )
        assertTrue(
            "\$config.child0Permits, \$config.child0PerSeconds" in rendered &&
                "\$config.child4Permits, \$config.child4PerSeconds" in rendered,
            "chart push must pass all 5 composite children",
        )
    }

    @Test
    fun `presets reset baseline fields so prior tweaks cannot leak across scenarios`() {
        // Regression: lowering workerConcurrency in the Tweak panel used to
        // bleed into subsequent presets, so "Under the limit" could render
        // artificially bottlenecked. Every preset must reset the shared
        // baseline (service time, jitter, failure rate, worker concurrency,
        // api target) to canonical defaults.
        for (id in listOf("under", "deny", "queue", "burst-drain", "smooth", "composite")) {
            val chunk =
                rendered
                    .substringAfter("id=\"preset-$id\"")
                    .substringBefore("</button>")
            assertTrue("\$config.serviceTimeMs = 50" in chunk, "preset $id should reset serviceTimeMs")
            assertTrue("\$config.jitterMs = 20" in chunk, "preset $id should reset jitterMs")
            assertTrue("\$config.failureRate = 0.0" in chunk, "preset $id should reset failureRate")
            assertTrue("\$config.workerConcurrency = 50" in chunk, "preset $id should reset workerConcurrency")
            assertTrue("\$config.apiTarget = 'none'" in chunk, "preset $id should reset apiTarget")
        }
    }

    @Test
    fun `tweak panel is a collapsible details element with all config sections`() {
        assertTrue("id=\"tweak-panel\"" in rendered, "tweak-panel missing")
        val tweak =
            rendered
                .substringAfter("id=\"tweak-panel\"")
                .substringBefore("id=\"status-log-panel\"")
        assertTrue("<summary>Tweak config</summary>" in tweak, "tweak-panel should have a summary header")
        assertTrue("id=\"tweak-limiter\"" in tweak, "tweak limiter section missing")
        assertTrue("id=\"tweak-permits\"" in tweak, "tweak permits section missing")
        assertTrue("id=\"tweak-traffic\"" in tweak, "tweak traffic section missing")
        // No $ui.step gating anywhere.
        assertTrue("\$ui.step" !in tweak, "tweak panel should not reference \$ui.step")
    }

    @Test
    fun `tweak-limiter section offers all three limiter choices`() {
        val section =
            rendered
                .substringAfter("id=\"tweak-limiter\"")
                .substringBefore("id=\"tweak-permits\"")
        for (value in listOf("bursty", "smooth", "composite")) {
            assertTrue("id=\"limiter-$value\"" in section, "missing limiter button $value")
            assertTrue(
                "\$config.limiterType = '$value'" in section,
                "limiter button $value should set limiterType",
            )
        }
        // Choosing a limiter while running should hot-swap via PATCH.
        assertTrue(
            "\$sim.running &amp;&amp; @patch('/simulations/' + \$sim.id)" in section,
            "limiter choice should PATCH live sim",
        )
    }

    @Test
    fun `tweak-permits section hides for composite and exposes permits slider`() {
        val section =
            rendered
                .substringAfter("id=\"tweak-permits\"")
                .substringBefore("id=\"tweak-traffic\"")
        assertTrue(
            "data-show=\"\$config.limiterType !== 'composite'\"" in section,
            "tweak-permits should be hidden for composite",
        )
        assertTrue("id=\"input-permits\"" in section, "permits slider missing")
        assertTrue(
            "id=\"input-permits\" type=\"range\" min=\"1\" max=\"500\"" in section,
            "permits range slider should have min=1",
        )
        assertTrue("data-bind=\"config.permits\"" in section, "permits slider should bind")
    }

    @Test
    fun `tweak-traffic section exposes rps slider and advanced fields`() {
        val section =
            rendered
                .substringAfter("id=\"tweak-traffic\"")
                .substringBefore("id=\"tweak-panel\"") // not-found; last-ish substring
        assertTrue("id=\"input-requestsPerSecond\"" in section, "rps slider missing")
        assertTrue("data-bind=\"config.requestsPerSecond\"" in section, "rps slider bind missing")
        assertTrue("traffic-advanced" in section, "advanced disclosure wrapper missing")
        assertTrue("<summary>Advanced</summary>" in section, "advanced summary label missing")
        val fields =
            listOf(
                "serviceTimeMs" to "config.serviceTimeMs",
                "jitterMs" to "config.jitterMs",
                "failureRate" to "config.failureRate",
                "workerConcurrency" to "config.workerConcurrency",
            )
        for ((field, signal) in fields) {
            assertTrue("id=\"input-$field\"" in section, "$field slider missing")
            assertTrue("data-bind=\"$signal\"" in section, "$field bind missing")
        }
    }

    @Test
    fun `status panel hosts the lifecycle controls and is always visible`() {
        val section =
            rendered
                .substringAfter("id=\"status-panel\"")
                .substringBefore("id=\"chart-panel\"")
        assertTrue("id=\"$LIFECYCLE_CONTROLS_ID\"" in section, "lifecycle-controls slot missing")
        assertTrue("id=\"start-button\"" in section, "start button missing")
        assertTrue("id=\"stop-button\"" in section, "stop button missing")
        // Run panels must not be gated on \$ui.step anymore — they are persistent
        // so the chart is visible before the user clicks anything.
        assertTrue("\$ui.step" !in section, "status panel should not reference \$ui.step")
    }

    @Test
    fun `run panels chart and stats render without ui step gating`() {
        val runPanels =
            rendered
                .substringAfter("id=\"run-panels\"")
                .substringBefore("id=\"tweak-panel\"")
        assertTrue("\$ui.step" !in runPanels, "run-panels should not be gated on \$ui.step")
        assertTrue("id=\"stats-panel\"" in runPanels)
        assertTrue("id=\"chart-panel\"" in runPanels)
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
    fun `chart script pushes denied rate as a distinct dataset`() {
        // Regression: the "Exceed · Deny" preset must produce a visible red line
        // on the chart, which requires the chart push to read rejectRate.
        assertTrue("rejectRate: \$stats.rejectRate" in rendered, "chart push should include rejectRate")
        assertTrue("'Denied/sec'" in rendered, "chart should have a Denied/sec dataset")
        assertTrue("s.rejectRate" in rendered, "chart push handler should forward rejectRate")
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
        val statusIdx = rendered.indexOf("id=\"status-log-panel\"")
        val logIdx = rendered.indexOf("id=\"log-panel\"")
        assertTrue(statusIdx > 0, "status-log-panel should exist")
        assertTrue(logIdx > 0, "log-panel should exist")
        assertTrue(statusIdx < logIdx, "status-log-panel should render before log-panel")
        assertTrue("Status Log" in rendered, "status log heading missing")
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
    fun `status badge is present inside status panel`() {
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
        assertTrue(
            "class=\"slider-value-input\" id=\"input-permits-value\" type=\"number\"" in rendered,
            "permits value input should be type=number",
        )
    }

    @Test
    fun `slider value inputs clamp and coerce on change`() {
        assertTrue(
            "\$config.serviceTimeMs = Math.min(500, Math.max(0, Math.round(" +
                "Number(\$config.serviceTimeMs) || 0)))" in rendered,
            "serviceTimeMs coercion missing",
        )
        assertTrue(
            "\$config.warmupSeconds = Math.min(30, Math.max(0, Number(\$config.warmupSeconds) || 0))" in
                rendered,
            "warmupSeconds coercion missing",
        )
        assertTrue(
            "\$config.workerConcurrency = Math.min(200, Math.max(1, Math.round(" +
                "Number(\$config.workerConcurrency) || 0)))" in rendered,
            "workerConcurrency coercion missing",
        )
    }

    @Test
    fun `permits value input can never coerce to zero`() {
        // Regression: the server rejects permits = 0 for non-composite limiters.
        assertTrue(
            "\$config.permits = Math.min(500, Math.max(1, Math.round(Number(\$config.permits) || 0)))" in
                rendered,
            "permits coercion must clamp to >= 1",
        )
        assertTrue(
            "id=\"input-permits-value\" type=\"number\" min=\"1\"" in rendered,
            "permits value input should have min=1",
        )
    }

    @Test
    fun `failure rate input stores 0 to 1 while displaying percent`() {
        assertTrue("data-bind=\"ui.failureRatePct\"" in rendered, "percent input should bind ui signal")
        assertTrue(
            "\$config.failureRate = Math.min(1, Math.max(0, (Number(\$ui.failureRatePct) || 0) / 100))" in
                rendered,
            "percent -> config sync missing",
        )
        assertTrue(
            "\$ui.failureRatePct = Math.round(\$config.failureRate * 100)" in rendered,
            "config -> percent sync missing",
        )
        assertTrue(
            "&quot;failureRatePct&quot;: 0" in rendered,
            "initial ui.failureRatePct missing from signals",
        )
    }
}
