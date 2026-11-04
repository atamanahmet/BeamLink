package com.beamlink.peer.event

import java.util.concurrent.ConcurrentHashMap

/**
 * Progress events for send and receive. Keeps the last event per slot.
 */
object ProgressBus {

    data class Slot(
        val bytes: Long,
        val total: Long,
        val state: String,
        val error: String?,
        val direction: String,
        val name: String?,
        val peer: String?,
    )

    private val slots = ConcurrentHashMap<String, Slot>()

    @Volatile
    var listener: ((
        bytes: Long,
        total: Long,
        state: String,
        error: String?,
        direction: String,
        name: String?,
        peer: String?,
    ) -> Unit)? = null

    /**
     * Stores the slot then notifies the WebView listener.
     */
    fun emit(
        bytes: Long,
        total: Long,
        state: String,
        error: String?,
        direction: String,
        name: String? = null,
        peer: String? = null,
    ) {
        slots[direction] = Slot(bytes, total, state, error, direction, name, peer)
        listener?.invoke(bytes, total, state, error, direction, name, peer)
    }

    /**
     * Last send and receive slots, if any.
     */
    fun snapshot(): List<Slot> {
        return listOfNotNull(slots["send"], slots["receive"])
    }
}
