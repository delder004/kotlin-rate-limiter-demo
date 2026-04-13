package com.example.web

import kotlinx.html.*

private const val DATASTAR_CDN =
    "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"

private val initialSignals = """
{
  "sim": { "id": null, "status": "idle", "running": false },
  "config": {
    "limiterType": "bursty",
    "permits": 5,
    "perSeconds": 1.0,
    "warmupSeconds": 0.0,
    "secondaryPermits": 0,
    "secondaryPerSeconds": 0.0,
    "requestsPerSecond": 5.0,
    "overflowMode": "queue",
    "apiTarget": "none",
    "serviceTimeMs": 50,
    "jitterMs": 20,
    "failureRate": 0.0,
    "workerConcurrency": 50
  },
  "stats": {
    "queued": 0,
    "inFlight": 0,
    "admitted": 0,
    "completed": 0,
    "denied": 0,
    "droppedIncoming": 0,
    "droppedOutgoing": 0,
    "acceptRate": 0,
    "rejectRate": 0,
    "avgLatencyMs": 0,
    "p50LatencyMs": 0,
    "p95LatencyMs": 0
  },
  "errors": { "form": null, "stream": null }
}
""".trimIndent()

private fun FlowContent.numberControl(
    field: String,
    labelText: String,
    step: String = "1",
    min: String? = null,
    max: String? = null,
) {
    div("control-group") {
        attributes["data-field"] = field
        label { htmlFor = "input-$field"; +labelText }
        numberInput {
            id = "input-$field"
            name = field
            attributes["data-bind"] = "config.$field"
            attributes["step"] = step
            if (min != null) attributes["min"] = min
            if (max != null) attributes["max"] = max
        }
        renderFieldErrorSlot(field)
    }
}

private fun FlowContent.selectControl(
    field: String,
    labelText: String,
    options: List<Pair<String, String>>,
) {
    div("control-group") {
        attributes["data-field"] = field
        label { htmlFor = "input-$field"; +labelText }
        select {
            id = "input-$field"
            name = field
            attributes["data-bind"] = "config.$field"
            attributes["data-on-change"] =
                "\$sim.running && @patch('/simulations/' + \$sim.id)"
            for ((value, text) in options) {
                option { attributes["value"] = value; +text }
            }
        }
        renderFieldErrorSlot(field)
    }
}

fun HTML.renderPageShell() {
    head {
        title { +"Rate Limiter Sandbox" }
        meta { charset = "utf-8" }
        meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
        script {
            type = "module"
            src = DATASTAR_CDN
        }
    }
    body {
        div {
            id = "page-root"
            attributes["data-signals"] = initialSignals

            header {
                h1 { +"Rate Limiter Sandbox" }
                p("subtitle") { +"A Datastar-driven dashboard for exploring rate-limiter behavior" }
            }

            section {
                id = "controls-panel"
                attributes["data-on-signal-change-config__debounce.300ms"] =
                    "\$sim.running && @patch('/simulations/' + \$sim.id)"
                h2 { +"Controls" }
                renderPresetsPanel()
                selectControl(
                    "limiterType",
                    "Limiter Type",
                    listOf("bursty" to "Bursty", "smooth" to "Smooth", "composite" to "Composite"),
                )
                numberControl("permits", "Permits", step = "1", min = "1")
                numberControl("perSeconds", "Per Seconds", step = "0.1", min = "0")
                numberControl("warmupSeconds", "Warmup Seconds", step = "0.1", min = "0")
                numberControl("secondaryPermits", "Secondary Permits", step = "1", min = "0")
                numberControl(
                    "secondaryPerSeconds",
                    "Secondary Per Seconds",
                    step = "0.1",
                    min = "0",
                )
                numberControl(
                    "requestsPerSecond",
                    "Requests / sec",
                    step = "0.1",
                    min = "0",
                )
                selectControl(
                    "overflowMode",
                    "Overflow Mode",
                    listOf("queue" to "Queue", "reject" to "Reject"),
                )
                selectControl(
                    "apiTarget",
                    "API Target",
                    listOf(
                        "none" to "None",
                        "catfact" to "Cat Fact",
                        "jsonplaceholder" to "JSON Placeholder",
                    ),
                )
                numberControl("serviceTimeMs", "Service Time (ms)", step = "1", min = "0")
                numberControl("jitterMs", "Jitter (ms)", step = "1", min = "0")
                numberControl(
                    "failureRate",
                    "Failure Rate",
                    step = "0.01",
                    min = "0",
                    max = "1",
                )
                numberControl(
                    "workerConcurrency",
                    "Worker Concurrency",
                    step = "1",
                    min = "1",
                )
            }

            section {
                id = "status-panel"
                h2 { +"Status" }
                p {
                    +"Simulation status: "
                    renderStatusBadgeSlot()
                }
                p {
                    +"Simulation ID: "
                    span { id = "sim-id"; attributes["data-text"] = "\$sim.id || '—'"; +"—" }
                }
                renderLifecycleControlsSlot()
            }

            section {
                id = "stats-panel"
                h2 { +"Stats" }
                ul {
                    li { +"Queued: "; span { attributes["data-text"] = "\$stats.queued"; +"0" } }
                    li { +"In flight: "; span { attributes["data-text"] = "\$stats.inFlight"; +"0" } }
                    li { +"Admitted: "; span { attributes["data-text"] = "\$stats.admitted"; +"0" } }
                    li { +"Completed: "; span { attributes["data-text"] = "\$stats.completed"; +"0" } }
                    li { +"Denied: "; span { attributes["data-text"] = "\$stats.denied"; +"0" } }
                    li { +"Dropped incoming: "; span { attributes["data-text"] = "\$stats.droppedIncoming"; +"0" } }
                    li { +"Dropped outgoing: "; span { attributes["data-text"] = "\$stats.droppedOutgoing"; +"0" } }
                    li { +"Accept rate: "; span { attributes["data-text"] = "\$stats.acceptRate"; +"0" } }
                    li { +"Reject rate: "; span { attributes["data-text"] = "\$stats.rejectRate"; +"0" } }
                    li { +"Avg latency (ms): "; span { attributes["data-text"] = "\$stats.avgLatencyMs"; +"0" } }
                    li { +"p50 latency (ms): "; span { attributes["data-text"] = "\$stats.p50LatencyMs"; +"0" } }
                    li { +"p95 latency (ms): "; span { attributes["data-text"] = "\$stats.p95LatencyMs"; +"0" } }
                }
            }

            section {
                id = "chart-panel"
                h2 { +"Chart" }
                renderChartMount()
                renderStreamAnchorSlot()
            }

            section {
                id = "log-panel"
                h2 { +"Log" }
                renderLogListSlot()
            }

            section {
                id = "errors-panel"
                h2 { +"Errors" }
                renderFormErrorsSlot()
                renderWarningsSlot()
                div("stream-errors") {
                    id = "errors-stream"
                    attributes["data-text"] = "\$errors.stream"
                }
            }
        }
    }
}
