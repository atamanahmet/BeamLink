package com.beamlink.peer.receive

import com.beamlink.peer.register.NexusPrefs

/**
 * In-memory reserve bytes. Reloads when the setting is saved.
 */
class DiskReserveProvider(private val prefs: NexusPrefs) {

    @Volatile
    private var bytes: Long = DiskReserve.DEFAULT_BYTES

    init {
        refresh()
    }

    /**
     * Reads the stored value or the default.
     */
    fun refresh() {
        bytes = prefs.minFreeSpaceBytes
    }

    fun getBytes(): Long = bytes
}
