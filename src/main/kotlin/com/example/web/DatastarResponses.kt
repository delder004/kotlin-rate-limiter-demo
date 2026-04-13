package com.example.web

import com.example.simulation.FieldError
import com.example.simulation.MetricsSnapshot
import com.example.simulation.SimulationEvent
import com.example.simulation.SimulationHandle
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

sealed class DatastarEvent {
    abstract fun render(): String

    data class MergeFragments(
        val html: String,
        val selector: String? = null,
        val mergeMode: String? = null,
    ) : DatastarEvent() {
        override fun render(): String = buildString {
            append("event: datastar-merge-fragments\n")
            if (selector != null) append("data: selector ").append(selector).append('\n')
            if (mergeMode != null) append("data: mergeMode ").append(mergeMode).append('\n')
            html.lineSequence().forEach { append("data: fragments ").append(it).append('\n') }
            append('\n')
        }
    }

    data class RemoveFragments(val selector: String) : DatastarEvent() {
        override fun render(): String =
            "event: datastar-remove-fragments\ndata: selector $selector\n\n"
    }

    data class MergeSignals(val json: String) : DatastarEvent() {
        override fun render(): String = buildString {
            append("event: datastar-merge-signals\n")
            json.lineSequence().forEach { append("data: signals ").append(it).append('\n') }
            append('\n')
        }
    }
}

class DatastarResponse {
    private val events = mutableListOf<DatastarEvent>()

    fun mergeFragment(
        html: String,
        selector: String? = null,
        mergeMode: String? = null,
    ) = apply { events += DatastarEvent.MergeFragments(html, selector, mergeMode) }

    fun removeFragment(selector: String) = apply { events += DatastarEvent.RemoveFragments(selector) }
    fun mergeSignals(json: String) = apply { events += DatastarEvent.MergeSignals(json) }
    fun add(event: DatastarEvent) = apply { events += event }

    fun body(): String = events.joinToString(separator = "") { it.render() }
}

fun DatastarResponse.patchFieldError(field: String, message: String?): DatastarResponse =
    mergeFragment(renderFieldErrorFragment(field, message))

fun DatastarResponse.patchFormErrors(
    errors: List<FieldError>,
    globalErrors: List<String> = emptyList(),
): DatastarResponse =
    mergeFragment(renderFormErrorsFragment(errors, globalErrors))

fun DatastarResponse.clearFormErrors(): DatastarResponse = apply {
    mergeFragment(renderFormErrorsFragment(emptyList()))
    ConfigFieldIds.forEach { mergeFragment(renderFieldErrorFragment(it, null)) }
}

fun DatastarResponse.patchSimStatus(status: String, running: Boolean): DatastarResponse =
    mergeSignals("""{"sim":{"status":"$status","running":$running}}""")

fun DatastarResponse.patchSimLifecycle(
    id: String?,
    status: String,
    running: Boolean,
): DatastarResponse {
    val idLiteral = if (id == null) "null" else "\"$id\""
    return mergeSignals("""{"sim":{"id":$idLiteral,"status":"$status","running":$running}}""")
}

fun DatastarResponse.patchLifecycleControls(
    handle: SimulationHandle?,
): DatastarResponse = apply {
    mergeFragment(renderStatusBadgeFragment(handle))
    mergeFragment(renderLifecycleControlsFragment(handle))
}

fun DatastarResponse.patchStreamAnchor(handle: SimulationHandle?): DatastarResponse =
    mergeFragment(renderStreamAnchorFragment(handle))

fun DatastarResponse.prependStatusLogEntry(message: String): DatastarResponse =
    mergeFragment(
        html = renderStatusLogEntryFragment(message),
        selector = "#$STATUS_LOG_ID",
        mergeMode = "prepend",
    )

fun buildStatsSignalsJson(s: MetricsSnapshot): String =
    """{"stats":{"queued":${s.queued},"inFlight":${s.inFlight},"admitted":${s.admitted},"completed":${s.completed},"denied":${s.denied},"droppedIncoming":${s.droppedIncoming},"droppedOutgoing":${s.droppedOutgoing},"acceptRate":${s.acceptRate},"rejectRate":${s.rejectRate},"avgLatencyMs":${s.avgLatencyMs},"p50LatencyMs":${s.p50LatencyMs},"p95LatencyMs":${s.p95LatencyMs}}}"""

fun buildSimLifecycleJson(id: String?, status: String, running: Boolean): String {
    val idLit = if (id == null) "null" else "\"$id\""
    return """{"sim":{"id":$idLit,"status":"$status","running":$running}}"""
}

object StreamEventMapper {
    fun initialStateEvents(handle: SimulationHandle): List<DatastarEvent> = listOf(
        DatastarEvent.MergeSignals(buildSimLifecycleJson(handle.id, handle.status.wire, handle.isRunning)),
        DatastarEvent.MergeSignals(buildStatsSignalsJson(handle.currentMetrics)),
    )

    fun toDatastarEvents(event: SimulationEvent): List<DatastarEvent> = when (event) {
        is SimulationEvent.Started -> emptyList()
        is SimulationEvent.MetricSample -> listOf(
            DatastarEvent.MergeSignals(buildStatsSignalsJson(event.snapshot)),
        )
        is SimulationEvent.ResponseSample -> listOf(
            DatastarEvent.MergeFragments(
                html = renderLogRowFragment(event.entry),
                selector = "#$LOG_LIST_ID",
                mergeMode = "prepend",
            ),
        )
        is SimulationEvent.Warning -> listOf(
            DatastarEvent.MergeFragments(
                html = renderStatusLogEntryFragment(event.message),
                selector = "#$STATUS_LOG_ID",
                mergeMode = "prepend",
            ),
        )
        is SimulationEvent.Stopped -> listOf(
            DatastarEvent.MergeSignals(buildSimLifecycleJson(null, "stopped", running = false)),
        )
        is SimulationEvent.Failed -> listOf(
            DatastarEvent.MergeSignals(buildSimLifecycleJson(null, "failed", running = false)),
        )
    }
}

private val DatastarContentType = ContentType.parse("text/event-stream")

suspend fun ApplicationCall.respondDatastar(response: DatastarResponse) {
    respondText(response.body(), DatastarContentType, HttpStatusCode.OK)
}
