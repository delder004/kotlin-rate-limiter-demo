package com.example.simulation

data class MetricsSnapshot(
    val timeMs: Long,
    val queued: Int,
    val inFlight: Int,
    val admitted: Long,
    val completed: Long,
    val denied: Long,
    val droppedIncoming: Long,
    val droppedOutgoing: Long,
    val acceptRate: Double,
    val rejectRate: Double,
    val avgLatencyMs: Long,
    val p50LatencyMs: Long,
    val p95LatencyMs: Long,
) {
    companion object {
        val Empty =
            MetricsSnapshot(
                timeMs = 0,
                queued = 0,
                inFlight = 0,
                admitted = 0,
                completed = 0,
                denied = 0,
                droppedIncoming = 0,
                droppedOutgoing = 0,
                acceptRate = 0.0,
                rejectRate = 0.0,
                avgLatencyMs = 0,
                p50LatencyMs = 0,
                p95LatencyMs = 0,
            )
    }
}

data class LogEntry(
    val timeMs: Long,
    val status: Int,
    val latencyMs: Long,
    val body: String,
)

class BoundedLogBuffer(val capacity: Int) {
    private val deque = ArrayDeque<LogEntry>(capacity)
    private val lock = Any()

    @Volatile
    var droppedCount: Long = 0L
        private set

    fun add(entry: LogEntry) {
        synchronized(lock) {
            if (deque.size >= capacity) {
                deque.removeFirst()
                droppedCount++
            }
            deque.addLast(entry)
        }
    }

    fun snapshot(): List<LogEntry> = synchronized(lock) { deque.toList() }

    fun size(): Int = synchronized(lock) { deque.size }
}
