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
        assertTrue("idle" in rendered)
    }

    @Test
    fun `shell contains controls panel`() {
        assertTrue("id=\"controls-panel\"" in rendered)
        assertTrue("Limiter Type" in rendered)
        assertTrue("Overflow Mode" in rendered)
    }

    @Test
    fun `shell contains status panel`() {
        assertTrue("id=\"status-panel\"" in rendered)
    }

    @Test
    fun `shell contains stats panel with all stats fields`() {
        assertTrue("id=\"stats-panel\"" in rendered)
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
    fun `shell contains chart panel`() {
        assertTrue("id=\"chart-panel\"" in rendered)
    }

    @Test
    fun `shell contains log panel with log-list slot`() {
        assertTrue("id=\"log-panel\"" in rendered)
        assertTrue("id=\"$LOG_LIST_ID\"" in rendered)
    }

    @Test
    fun `shell contains warnings slot`() {
        assertTrue("id=\"$WARNINGS_ID\"" in rendered)
    }

    @Test
    fun `shell contains errors panel with global form error slot`() {
        assertTrue("id=\"errors-panel\"" in rendered)
        assertTrue("id=\"errors-form\"" in rendered)
        assertTrue("id=\"errors-stream\"" in rendered)
    }

    @Test
    fun `shell contains per-field error slots for every config field`() {
        for (field in ConfigFieldIds) {
            val selector = "id=\"${fieldErrorId(field)}\""
            assertTrue(selector in rendered, "missing field error slot for $field")
        }
    }

    @Test
    fun `shell contains lifecycle controls and status badge`() {
        assertTrue("id=\"$STATUS_BADGE_ID\"" in rendered)
        assertTrue("id=\"$LIFECYCLE_CONTROLS_ID\"" in rendered)
        assertTrue("id=\"start-button\"" in rendered)
        assertTrue("id=\"stop-button\"" in rendered)
    }

    @Test
    fun `shell contains stream-anchor placeholder`() {
        assertTrue("id=\"$STREAM_ANCHOR_ID\"" in rendered)
    }

    @Test
    fun `shell contains presets panel with preset buttons`() {
        assertTrue("id=\"$PRESETS_ID\"" in rendered)
        assertTrue("id=\"preset-low\"" in rendered)
        assertTrue("id=\"preset-burst\"" in rendered)
        assertTrue("id=\"preset-smooth\"" in rendered)
    }

    @Test
    fun `controls panel has debounced update wiring for running simulations`() {
        assertTrue(
            "data-on-signal-change-config__debounce.300ms" in rendered,
            "expected debounced config change wiring",
        )
        assertTrue(
            "@patch('/simulations/' + \$sim.id)" in rendered,
            "expected patch action on config change",
        )
    }

    @Test
    fun `shell contains chart mount point`() {
        assertTrue("id=\"$CHART_MOUNT_ID\"" in rendered)
    }

    @Test
    fun `select controls carry immediate on-change patch wiring`() {
        for (field in listOf("limiterType", "overflowMode", "apiTarget")) {
            val selectFragment = rendered
                .substringAfter("id=\"input-$field\"")
                .substringBefore("</select>")
            assertTrue(
                "data-on-change=" in selectFragment,
                "expected data-on-change on <select> $field",
            )
            assertTrue(
                "@patch('/simulations/' + \$sim.id)" in selectFragment,
                "expected @patch wiring on <select> $field",
            )
        }
    }

    @Test
    fun `controls render as real editable form inputs bound to config signals`() {
        // selects for enum-like fields
        for (field in listOf("limiterType", "overflowMode", "apiTarget")) {
            assertTrue(
                "<select" in rendered && "id=\"input-$field\"" in rendered,
                "expected <select> for $field",
            )
            assertTrue(
                "data-bind=\"config.$field\"" in rendered,
                "expected data-bind on $field",
            )
        }

        // numeric inputs for numeric fields
        val numericFields = listOf(
            "permits",
            "perSeconds",
            "warmupSeconds",
            "secondaryPermits",
            "secondaryPerSeconds",
            "requestsPerSecond",
            "serviceTimeMs",
            "jitterMs",
            "failureRate",
            "workerConcurrency",
        )
        for (field in numericFields) {
            assertTrue("id=\"input-$field\"" in rendered, "missing input for $field")
            assertTrue(
                "data-bind=\"config.$field\"" in rendered,
                "expected data-bind on $field",
            )
        }
        // confirm the rendered page has at least one type=number input
        assertTrue("type=\"number\"" in rendered, "expected number input")
    }

    @Test
    fun `shell includes datastar script`() {
        assertTrue("datastar" in rendered, "datastar script missing")
    }
}
