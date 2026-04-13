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
