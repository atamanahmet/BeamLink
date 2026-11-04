package com.beamlink.peer.receive

import android.annotation.SuppressLint
import java.io.File

/**
 * Disk check before allocating a receive temp file.
 */
object DiskSpace {

    /**
     * Hard reserve uses getUsableSpace. Cache reclaim is not free.
     */
    @SuppressLint("UsableSpace")
    fun usableBytes(dir: File): Long {
        return dir.usableSpace
    }

    /**
     * True when the dir has at least required free bytes.
     */
    fun hasRoom(dir: File, required: Long): Boolean {
        if (required <= 0L) {
            return true
        }
        return usableBytes(dir) >= required
    }
}
