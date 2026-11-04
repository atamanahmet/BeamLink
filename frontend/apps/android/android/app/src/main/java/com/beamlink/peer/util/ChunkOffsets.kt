package com.beamlink.peer.util

/**
 * Pure offset helpers matching the desktop Content-Range chunk math.
 */
object ChunkOffsets {

    /**
     * Builds Content-Range for one chunk: bytes start-end/total.
     */
    fun contentRange(offset: Long, bytesRead: Int, fileSize: Long): String {
        require(bytesRead > 0) { "bytesRead must be positive" }
        require(offset >= 0L) { "offset must be non-negative" }
        val chunkEnd = offset + bytesRead - 1L
        return "bytes $offset-$chunkEnd/$fileSize"
    }

    /**
     * Next read offset after a successful chunk.
     */
    fun nextOffset(offset: Long, bytesRead: Int): Long {
        require(bytesRead > 0) { "bytesRead must be positive" }
        return offset + bytesRead
    }
}
