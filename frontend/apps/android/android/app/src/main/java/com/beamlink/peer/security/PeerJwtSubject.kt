package com.beamlink.peer.security

import java.util.UUID

/**
 * Parses a JWT subject as a device UUID.
 */
object PeerJwtSubject {

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
