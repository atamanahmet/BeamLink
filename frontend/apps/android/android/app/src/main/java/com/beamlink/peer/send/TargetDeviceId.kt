package com.beamlink.peer.send

import java.util.UUID

/**
 * Parses a send target device id as a UUID.
 */
object TargetDeviceId {

    /**
     * Returns a UUID when the text is a valid UUID, else null.
     */
    fun parseUuidOrNull(raw: String?): UUID? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return try {
            UUID.fromString(raw)
        } catch (_: Exception) {
            null
        }
    }
}
