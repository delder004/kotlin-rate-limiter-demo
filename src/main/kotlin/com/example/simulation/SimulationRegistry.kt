package com.example.simulation

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SimulationRegistry(
    private val engine: SimulationEngine = NoopSimulationEngine,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = DefaultIdGenerator(),
) {
    private val handles = ConcurrentHashMap<String, SimulationHandle>()

    fun create(config: SimulationConfig): SimulationHandle {
        val handle =
            SimulationHandle(
                id = idGenerator(),
                initialConfig = config,
                createdAt = Instant.now(clock),
            )
        handles[handle.id] = handle
        engine.start(handle)
        return handle
    }

    fun get(id: String): SimulationHandle? = handles[id]

    fun list(): List<SimulationHandle> = handles.values.toList()

    fun update(
        id: String,
        newConfig: SimulationConfig,
    ): UpdateResult {
        val handle = handles[id] ?: return UpdateResult.NotFound
        synchronized(handle) {
            // Guard the race between update and stop: update may have captured
            // this handle reference before stop's handles.remove() and only now
            // reached the synchronized block. If stop won the lock first, the
            // handle is no longer live and we must not mutate it or echo a
            // stale lifecycle patch back to the client.
            if (handle.status != SimulationStatus.RUNNING) {
                return UpdateResult.NotRunning
            }
            val oldConfig = handle.config
            handle.applyUpdate(newConfig, Instant.now(clock))
            // Only rebuild the limiter when something that actually changes
            // its capacity policy moves. Rebuilding on every PATCH would hand
            // workers a fresh, full token bucket each time a slider twitched,
            // producing misleading throughput surges above the permits line.
            if (limiterShapeChanged(oldConfig, newConfig)) {
                handle.limiterRef.set(LimiterFactory.create(newConfig))
            }
            val diff = configDiff(oldConfig, newConfig)
            val message = if (diff.isEmpty()) "Config Updated" else "Config Updated: $diff"
            handle.appendLog(
                LogEntry(
                    timeMs = 0,
                    status = 0,
                    latencyMs = 0,
                    body = message,
                ),
            )
            handle.publish(SimulationEvent.Warning(handle.id, message))
        }
        return UpdateResult.Updated(handle)
    }

    private fun limiterShapeChanged(
        old: SimulationConfig,
        new: SimulationConfig,
    ): Boolean =
        old.limiterType != new.limiterType ||
            old.permits != new.permits ||
            old.perSeconds != new.perSeconds ||
            old.warmupSeconds != new.warmupSeconds ||
            old.compositeChildren != new.compositeChildren

    private fun configDiff(
        old: SimulationConfig,
        new: SimulationConfig,
    ): String {
        val changes = mutableListOf<String>()

        fun <T> check(
            name: String,
            o: T,
            n: T,
        ) {
            if (o != n) changes += "$name $o→$n"
        }
        check("limiter", old.limiterType.wire, new.limiterType.wire)
        check("permits", old.permits, new.permits)
        check("perSeconds", old.perSeconds, new.perSeconds)
        check("requestsPerSecond", old.requestsPerSecond, new.requestsPerSecond)
        check("overflow", old.overflowMode.wire, new.overflowMode.wire)
        check("serviceTimeMs", old.serviceTimeMs, new.serviceTimeMs)
        check("jitterMs", old.jitterMs, new.jitterMs)
        check("failureRate", old.failureRate, new.failureRate)
        return changes.joinToString(", ")
    }

    fun stop(id: String): SimulationHandle? {
        val handle = handles.remove(id) ?: return null
        synchronized(handle) {
            if (handle.status != SimulationStatus.STOPPED) {
                handle.status = SimulationStatus.STOPPED
                handle.stoppedAt = Instant.now(clock)
                handle.engineJob?.cancel()
                handle.publish(SimulationEvent.Stopped(handle.id))
                handle.closeAllSubscribers()
            }
        }
        return handle
    }

    sealed class UpdateResult {
        data class Updated(val handle: SimulationHandle) : UpdateResult()

        object NotFound : UpdateResult()

        object NotRunning : UpdateResult()
    }

    private class DefaultIdGenerator : () -> String {
        private val counter = AtomicLong(0)

        override fun invoke(): String {
            val n = counter.incrementAndGet()
            val suffix = java.util.UUID.randomUUID().toString().take(8)
            return "sim-$n-$suffix"
        }
    }
}
