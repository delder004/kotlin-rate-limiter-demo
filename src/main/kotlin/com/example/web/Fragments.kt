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
                "(\$ui.step = Math.max(\$ui.step, 5), @post('/simulations'))"
            attributes["data-attr-disabled"] = "\$sim.running"
            +"Start!"
        }
        button(type = ButtonType.button, classes = "stop-button") {
            id = "stop-button"
            attributes["data-on-click"] = "@delete('/simulations/' + \$sim.id)"
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

data class SimulationPreset(
    val id: String,
    val label: String,
    val updates: Map<String, String>,
)

val DefaultPresets: List<SimulationPreset> =
    listOf(
        SimulationPreset(
            id = "low",
            label = "Low (1 rps)",
            updates =
                mapOf(
                    "requestsPerSecond" to "1",
                    "limiterType" to "bursty",
                    "permits" to "5",
                    "perSeconds" to "1.0",
                ),
        ),
        SimulationPreset(
            id = "burst",
            label = "Burst (100 rps)",
            updates =
                mapOf(
                    "requestsPerSecond" to "100",
                    "limiterType" to "bursty",
                    "permits" to "20",
                    "perSeconds" to "1.0",
                ),
        ),
        SimulationPreset(
            id = "smooth",
            label = "Smooth ramp",
            updates =
                mapOf(
                    "requestsPerSecond" to "50",
                    "limiterType" to "smooth",
                    "permits" to "10",
                    "perSeconds" to "1.0",
                    "warmupSeconds" to "3",
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
                val signalUpdates =
                    preset.updates.entries.joinToString(", ") { (k, v) ->
                        val literal = if (v.toDoubleOrNull() != null) v else "'$v'"
                        "\$config.$k = $literal"
                    }
                attributes["data-on-click"] =
                    "($signalUpdates, \$sim.running && @patch('/simulations/' + \$sim.id))"
                +preset.label
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
            attributes["data-text"] =
                "(window.__chartPush ? (window.__chartPush({" +
                "running: \$sim.running, " +
                "completed: \$stats.completed, " +
                "acceptRate: \$stats.acceptRate, " +
                "permitsPerSec: \$config.permits / (\$config.perSeconds || 1), " +
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
                "(\$ui.step = Math.max(\$ui.step, 5), @post('/simulations'))"
            if (running) attributes["disabled"] = "disabled"
            +"Start!"
        }
        button(type = ButtonType.button, classes = "stop-button") {
            id = "stop-button"
            attributes["data-on-click"] = "@delete('/simulations/' + \$sim.id)"
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
