package com.example.simulation

import kotlinx.coroutines.Job
import java.time.Instant
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class SimulationStatus(val wire: String) {
    RUNNING("running"),
    STOPPED("stopped"),
    FAILED("failed"),
}

class SimulationHandle internal constructor(
    val id: String,
    initialConfig: SimulationConfig,
    val createdAt: Instant,
    logBufferCapacity: Int = DEFAULT_LOG_CAPACITY,
) {
    @Volatile
    var config: SimulationConfig = initialConfig
        internal set

    @Volatile
    var status: SimulationStatus = SimulationStatus.RUNNING
        internal set

    @Volatile
    var stoppedAt: Instant? = null
        internal set

    @Volatile
    var updatedAt: Instant? = null
        internal set

    private val metricsRef = AtomicReference(MetricsSnapshot.Empty)
    private val logs = BoundedLogBuffer(logBufferCapacity)
    private val subscribers = CopyOnWriteArraySet<SimulationSubscriber>()
    private val droppedOutgoingRef = AtomicLong(0)

    internal val limiterRef: AtomicReference<EngineLimiter?> = AtomicReference(null)

    @Volatile
    internal var engineJob: Job? = null

    val isRunning: Boolean
        get() = status == SimulationStatus.RUNNING

    val currentMetrics: MetricsSnapshot
        get() = metricsRef.get()

    val recentLogs: List<LogEntry>
        get() = logs.snapshot()

    val droppedLogCount: Long
        get() = logs.droppedCount

    val droppedOutgoingCount: Long
        get() = droppedOutgoingRef.get()

    val subscriberCount: Int
        get() = subscribers.size

    fun attachSubscriber(capacity: Int = SimulationSubscriber.DEFAULT_CAPACITY): SimulationSubscriber {
        val sub = SimulationSubscriber(capacity)
        subscribers.add(sub)
        return sub
    }

    // Serializes with SimulationRegistry.stop() via the handle's monitor.
    // Either we observe RUNNING and our subscriber joins the set before stop's
    // synchronized block snapshots subscribers for closeAllSubscribers (so stop
    // will close us and the stream loop exits cleanly), or stop already ran and
    // we see a non-RUNNING status and refuse to attach. Prevents a late attach
    // after closeAllSubscribers has already drained, which would otherwise
    // leave the SSE handler blocked on a channel nobody will ever close.
    //
    // The returned snapshot captures id/status/metrics under the same lock so
    // the SSE handler can emit an initial state that is coherent with the
    // moment of attachment — without the capture, a stop() that lands between
    // attach and the handler's read of handle.status could surface a "stopped"
    // initial snapshot for a client that connected to a running sim.
    fun attachSubscriberIfRunning(capacity: Int = SimulationSubscriber.DEFAULT_CAPACITY): AttachedSnapshot? =
        synchronized(this) {
            if (status != SimulationStatus.RUNNING) return@synchronized null
            AttachedSnapshot(
                subscriber = attachSubscriber(capacity),
                simId = id,
                statusWire = status.wire,
                running = isRunning,
                metrics = currentMetrics,
            )
        }

    data class AttachedSnapshot(
        val subscriber: SimulationSubscriber,
        val simId: String,
        val statusWire: String,
        val running: Boolean,
        val metrics: MetricsSnapshot,
    )

    fun detachSubscriber(subscriber: SimulationSubscriber) {
        if (subscribers.remove(subscriber)) {
            subscriber.close()
        }
    }

    internal fun publish(event: SimulationEvent) {
        for (sub in subscribers) {
            if (!sub.offer(event)) {
                droppedOutgoingRef.incrementAndGet()
            }
        }
    }

    internal fun closeAllSubscribers() {
        val snapshot = subscribers.toList()
        subscribers.clear()
        for (sub in snapshot) sub.close()
    }

    internal fun updateMetrics(snapshot: MetricsSnapshot) {
        metricsRef.set(snapshot)
    }

    internal fun applyUpdate(
        newConfig: SimulationConfig,
        at: Instant,
    ) {
        config = newConfig
        updatedAt = at
    }

    internal fun appendLog(entry: LogEntry) {
        logs.add(entry)
    }

    override fun toString(): String = "SimulationHandle(id=$id, status=$status, createdAt=$createdAt, stoppedAt=$stoppedAt)"

    companion object {
        const val DEFAULT_LOG_CAPACITY = 200
    }
}
