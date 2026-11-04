package com.beamlink.peer.receive

import org.json.JSONObject

/**
 * Pure checks for a receive fileSize field. Zero is allowed.
 */
object FileSizeRule {

    const val CODE = "INVALID_FILE_SIZE"

    /**
     * True when size is missing or negative.
     */
    fun invalid(fileSize: Long?): Boolean {
        return fileSize == null || fileSize < 0L
    }

    /**
     * Returns the size or throws INVALID_FILE_SIZE 400.
     */
    fun requireValid(fileSize: Long?): Long {
        if (fileSize == null || fileSize < 0L) {
            throw ProtocolException(CODE, "Invalid file size: $fileSize", 400)
        }
        return fileSize
    }

    /**
     * Reads fileSize as Long. Missing, negative, or not a number is 400.
     */
    fun parse(json: JSONObject, key: String = "fileSize"): Long {
        if (!json.has(key) || json.isNull(key)) {
            throw ProtocolException(CODE, "Invalid file size: null", 400)
        }
        val n = try {
            json.getLong(key)
        } catch (_: Exception) {
            throw ProtocolException(CODE, "Invalid file size: ${json.opt(key)}", 400)
        }
        return requireValid(n)
    }
}
