package com.example.simulation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

class SimulationSubscriber internal constructor(capacity: Int = DEFAULT_CAPACITY) {
    private val channel = Channel<SimulationEvent>(capacity)

    val events: ReceiveChannel<SimulationEvent> get() = channel

    internal fun offer(event: SimulationEvent): Boolean = channel.trySend(event).isSuccess

    internal fun close() {
        channel.close()
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 128
    }
}
