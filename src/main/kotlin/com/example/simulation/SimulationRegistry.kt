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
        val handle = SimulationHandle(
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

    fun update(id: String, newConfig: SimulationConfig): UpdateResult {
        val handle = handles[id] ?: return UpdateResult.NotFound
        synchronized(handle) {
            if (handle.status != SimulationStatus.RUNNING) {
                return UpdateResult.NotRunning
            }
            handle.engineJob?.cancel()
            handle.resetForUpdate(newConfig, Instant.now(clock))
            handle.appendLog(
                LogEntry(
                    timeMs = 0,
                    status = 0,
                    latencyMs = 0,
                    body = "config updated",
                ),
            )
            handle.publish(SimulationEvent.Warning(handle.id, "config updated"))
            engine.start(handle)
        }
        return UpdateResult.Updated(handle)
    }

    fun stop(id: String): SimulationHandle? {
        val handle = handles[id] ?: return null
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
