package com.beamlink.peer.send

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-call connect-phase flags. Concurrent. Remove is safe twice.
 */
class ConnectPhaseStore {

    class Entry {
        @Volatile
        var connectFailed: Boolean = false
            private set

        /**
         * Marks this call as a connect-phase failure.
         */
        fun markConnectFailed() {
            connectFailed = true
        }
    }

    private val live = ConcurrentHashMap<Any, Entry>()

    /**
     * Returns the entry for this key, creating one if missing.
     */
    fun create(key: Any): Entry {
        return live.computeIfAbsent(key) { Entry() }
    }

    /**
     * Returns the entry still stored for this key, or null.
     */
    fun get(key: Any): Entry? {
        return live[key]
    }

    /**
     * Drops the entry. A second remove is a no-op.
     */
    fun remove(key: Any) {
        live.remove(key)
    }
}
