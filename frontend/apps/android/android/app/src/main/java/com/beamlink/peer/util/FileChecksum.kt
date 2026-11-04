package com.beamlink.peer.util

import java.security.MessageDigest

/**
 * Chunk SHA-256 helpers matching the desktop FileChecksum.
 */
object FileChecksum {

    private val hexChars = "0123456789abcdef".toCharArray()

    /**
     * Lowercase hex SHA-256 of the given bytes.
     */
    fun sha256Hex(data: ByteArray, offset: Int = 0, length: Int = data.size): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data, offset, length)
        return toHex(digest.digest())
    }

    private fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xff
            out[i++] = hexChars[v ushr 4]
            out[i++] = hexChars[v and 0x0f]
        }
        return String(out)
    }
}
