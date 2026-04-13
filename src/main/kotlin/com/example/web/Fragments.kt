package com.example.web

import com.example.simulation.FieldError
import com.example.simulation.LogEntry
import com.example.simulation.SimulationHandle
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.canvas
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

val ConfigFieldIds: List<String> =
    listOf(
        "limiterType",
        "permits",
        "perSeconds",
        "warmupSeconds",
        "secondaryPermits",
        "secondaryPerSeconds",
        "requestsPerSecond",
        "overflowMode",
        "apiTarget",
        "serviceTimeMs",
        "jitterMs",
        "failureRate",
        "workerConcurrency",
    )

fun fieldErrorId(field: String): String = "field-error-$field"

fun FlowContent.renderFieldErrorSlot(field: String) {
    div("field-error") {
        id = fieldErrorId(field)
    }
}

fun FlowContent.renderFormErrorsSlot() {
    div("form-errors") {
        id = "errors-form"
    }
}

fun renderFieldErrorFragment(
    field: String,
    message: String?,
): String =
    createHTML().div("field-error") {
        id = fieldErrorId(field)
        if (!message.isNullOrEmpty()) +message
    }

fun renderFormErrorsFragment(
    errors: List<FieldError>,
    globalErrors: List<String> = emptyList(),
): String =
    createHTML().div("form-errors") {
        id = "errors-form"
        if (globalErrors.isNotEmpty() || errors.isNotEmpty()) {
            ul {
                globalErrors.forEach { li { +it } }
                errors.forEach { li { +"${it.field}: ${it.message}" } }
            }
        }
    }

const val LIFECYCLE_CONTROLS_ID = "lifecycle-controls"
const val STATUS_BADGE_ID = "status-badge"
const val STREAM_ANCHOR_ID = "stream-anchor"
const val LOG_LIST_ID = "log-list"
const val STATUS_LOG_ID = "status-log-list"
const val PRESETS_ID = "presets-panel"
const val CHART_MOUNT_ID = "chart-mount"

fun FlowContent.renderStatusBadgeSlot() {
    span("status-badge status-badge-idle") {
        id = STATUS_BADGE_ID
        attributes["data-text"] = "\$sim.status"
        +"idle"
    }
}

fun FlowContent.renderLifecycleControlsSlot() {
    div("lifecycle-controls") {
        id = LIFECYCLE_CONTROLS_ID
        button(type = ButtonType.button, classes = "start-button") {
            id = "start-button"
            attributes["data-on-click"] =
                "(window.__cancelFollowUp && window.__cancelFollowUp(), @post('/simulations'))"
            attributes["data-attr-disabled"] = "\$sim.running"
            +"Start!"
        }
        button(type = ButtonType.button, classes = "stop-button") {
            id = "stop-button"
            attributes["data-on-click"] =
                "(window.__cancelFollowUp && window.__cancelFollowUp(), @delete('/simulations/' + \$sim.id))"
            attributes["data-attr-disabled"] = "!\$sim.running"
            +"Stop"
        }
    }
}

fun FlowContent.renderStreamAnchorSlot() {
    div("stream-anchor") {
        id = STREAM_ANCHOR_ID
    }
}

fun FlowContent.renderLogListSlot() {
    div("log-list") {
        id = LOG_LIST_ID
    }
}

fun FlowContent.renderStatusLogSlot() {
    div("status-log-list") {
        id = STATUS_LOG_ID
    }
}

fun renderStreamAnchorFragment(handle: SimulationHandle?): String =
    createHTML().div("stream-anchor") {
        id = STREAM_ANCHOR_ID
        if (handle != null && handle.isRunning) {
            attributes["data-on-load"] = "@get('/simulations/${handle.id}/stream')"
        }
    }

fun renderLogRowFragment(entry: LogEntry): String =
    createHTML().div("log-row log-row-${entry.status}") {
        +"[${entry.timeMs}ms] status=${entry.status} latency=${entry.latencyMs}ms ${entry.body}"
    }

fun renderStatusLogEntryFragment(message: String): String =
    createHTML().div("status-log-row") {
        +message
    }

fun renderEmptyStatusLogFragment(): String =
    createHTML().div("status-log-list") {
        id = STATUS_LOG_ID
    }

data class PresetFollowUp(
    val delayMs: Int,
    val requestsPerSecond: Int,
)

data class SimulationPreset(
    val id: String,
    val label: String,
    val description: String,
    val updates: Map<String, String>,
    val followUp: PresetFollowUp? = null,
)

// Fields the tweak panel exposes that every preset must reset to canonical
// defaults, so a prior manual tweak (e.g. dropping workerConcurrency to 5)
// doesn't silently break the next preset's scenario. Presets override any
// baseline field they care about via their own `updates` map.
val PresetBaseline: Map<String, String> =
    mapOf(
        "serviceTimeMs" to "50",
        "jitterMs" to "20",
        "failureRate" to "0.0",
        "workerConcurrency" to "50",
        "apiTarget" to "none",
    )

val DefaultPresets: List<SimulationPreset> =
    listOf(
        SimulationPreset(
            id = "under",
            label = "Under the limit",
            description = "10 req/s through a 20/s limiter — limiter stays out of the way.",
            updates =
                mapOf(
                    "limiterType" to "bursty",
                    "permits" to "20",
                    "perSeconds" to "1.0",
                    "warmupSeconds" to "0",
                    "requestsPerSecond" to "10",
                    "overflowMode" to "queue",
                ),
        ),
        SimulationPreset(
            id = "deny",
            label = "Exceed the limit · Deny",
            description = "40 req/s into a 10/s limiter — watch the red denied gap open up.",
            updates =
                mapOf(
                    "limiterType" to "bursty",
                    "permits" to "10",
                    "perSeconds" to "1.0",
                    "warmupSeconds" to "0",
                    "requestsPerSecond" to "40",
                    "overflowMode" to "reject",
                ),
        ),
        SimulationPreset(
            id = "queue",
            label = "Exceed the limit · Queue",
            description = "Same overflow, buffered instead of dropped — latency climbs.",
            updates =
                mapOf(
                    "limiterType" to "bursty",
                    "permits" to "10",
                    "perSeconds" to "1.0",
                    "warmupSeconds" to "0",
                    "requestsPerSecond" to "40",
                    "overflowMode" to "queue",
                ),
        ),
        SimulationPreset(
            id = "burst-drain",
            label = "Burst then drain",
            description = "60 req/s spike for 5s, then drops to 5 req/s — limiter shaves the spike.",
            updates =
                mapOf(
                    "limiterType" to "bursty",
                    "permits" to "10",
                    "perSeconds" to "1.0",
                    "warmupSeconds" to "0",
                    "requestsPerSecond" to "60",
                    "overflowMode" to "reject",
                ),
            followUp = PresetFollowUp(delayMs = 5000, requestsPerSecond = 5),
        ),
        SimulationPreset(
            id = "smooth",
            label = "Smooth warmup",
            description = "Smooth limiter ramps up over 5s before reaching 20/s.",
            updates =
                mapOf(
                    "limiterType" to "smooth",
                    "permits" to "20",
                    "perSeconds" to "1.0",
                    "warmupSeconds" to "5",
                    "requestsPerSecond" to "20",
                    "overflowMode" to "queue",
                ),
        ),
        SimulationPreset(
            id = "composite",
            label = "Tiered limits",
            description = "Two stacked limits: 20/s AND 120/10s. Long-run ceiling kicks in.",
            updates =
                mapOf(
                    "limiterType" to "composite",
                    "compositeCount" to "2",
                    "child0Type" to "bursty",
                    "child0Permits" to "20",
                    "child0PerSeconds" to "1",
                    "child1Type" to "bursty",
                    "child1Permits" to "120",
                    "child1PerSeconds" to "10",
                    "requestsPerSecond" to "30",
                    "overflowMode" to "reject",
                ),
        ),
    )

fun FlowContent.renderPresetsPanel() {
    div {
        id = PRESETS_ID
        classes = setOf("presets")
        for (preset in DefaultPresets) {
            button(type = ButtonType.button, classes = "preset-button") {
                id = "preset-${preset.id}"
                attributes["data-preset"] = preset.id
                // Merge baseline first so preset.updates overrides any field
                // it cares about. Ordering matters: baseline assignments run
                // first in the emitted expression, so the preset's own values
                // land last and win for shared keys.
                val mergedUpdates = PresetBaseline + preset.updates
                val signalUpdates =
                    mergedUpdates.entries.joinToString(", ") { (k, v) ->
                        val literal = if (v.toDoubleOrNull() != null) v else "'$v'"
                        "\$config.$k = $literal"
                    }
                val followUpCall =
                    preset.followUp?.let { fu ->
                        ", window.__scheduleFollowUp(${fu.delayMs}, ${fu.requestsPerSecond})"
                    } ?: ""
                // Every preset click cancels any pending burst-drain drop
                // from a prior click, so switching presets mid-scenario
                // doesn't get clobbered by the old timeout firing.
                attributes["data-on-click"] =
                    "(window.__cancelFollowUp && window.__cancelFollowUp(), " +
                    "$signalUpdates, " +
                    "!\$sim.running && @post('/simulations'), " +
                    "\$sim.running && @patch('/simulations/' + \$sim.id)" +
                    "$followUpCall)"
                div("preset-label") { +preset.label }
                div("preset-description") { +preset.description }
            }
        }
    }
}

const val CHART_CANVAS_ID = "metrics-chart"

fun FlowContent.renderChartMount() {
    div {
        id = CHART_MOUNT_ID
        classes = setOf("chart-mount")
        attributes["data-chart"] = "stats"
        canvas {
            id = CHART_CANVAS_ID
        }
        span("chart-effect") {
            // permitsPerSec routes through __effectivePermitsPerSec so the
            // composite case returns the slowest child's steady-state rate
            // (the bottleneck tier) rather than a stale top-level value.
            attributes["data-text"] =
                "(window.__chartPush ? (window.__chartPush({" +
                "running: \$sim.running, " +
                "completed: \$stats.completed, " +
                "acceptRate: \$stats.acceptRate, " +
                "rejectRate: \$stats.rejectRate, " +
                "permitsPerSec: (window.__effectivePermitsPerSec ? " +
                "window.__effectivePermitsPerSec(" +
                "\$config.limiterType, \$config.permits, \$config.perSeconds, \$config.compositeCount, " +
                "\$config.child0Permits, \$config.child0PerSeconds, " +
                "\$config.child1Permits, \$config.child1PerSeconds, " +
                "\$config.child2Permits, \$config.child2PerSeconds, " +
                "\$config.child3Permits, \$config.child3PerSeconds, " +
                "\$config.child4Permits, \$config.child4PerSeconds" +
                ") : (\$config.permits / (\$config.perSeconds || 1))), " +
                "incomingPerSec: \$config.requestsPerSecond" +
                "}), '') : '')"
        }
    }
}

fun renderStatusBadgeFragment(handle: SimulationHandle?): String =
    createHTML().span("status-badge ${badgeClass(handle)}") {
        id = STATUS_BADGE_ID
        attributes["data-text"] = "\$sim.status"
        +(handle?.status?.wire ?: "idle")
    }

fun renderLifecycleControlsFragment(handle: SimulationHandle?): String {
    val running = handle?.isRunning == true
    return createHTML().div("lifecycle-controls") {
        id = LIFECYCLE_CONTROLS_ID
        button(type = ButtonType.button, classes = "start-button") {
            id = "start-button"
            attributes["data-on-click"] =
                "(window.__cancelFollowUp && window.__cancelFollowUp(), @post('/simulations'))"
            if (running) attributes["disabled"] = "disabled"
            +"Start!"
        }
        button(type = ButtonType.button, classes = "stop-button") {
            id = "stop-button"
            attributes["data-on-click"] =
                "(window.__cancelFollowUp && window.__cancelFollowUp(), @delete('/simulations/' + \$sim.id))"
            if (!running) attributes["disabled"] = "disabled"
            +"Stop"
        }
    }
}

private fun badgeClass(handle: SimulationHandle?): String =
    when {
        handle == null -> "status-badge-idle"
        handle.isRunning -> "status-badge-running"
        else -> "status-badge-stopped"
    }
