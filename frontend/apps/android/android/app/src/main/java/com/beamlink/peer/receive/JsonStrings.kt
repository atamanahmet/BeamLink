package com.beamlink.peer.receive

import org.json.JSONObject

/**
 * Reads optional JSON strings without a null fallback.
 */
object JsonStrings {

    /**
     * Returns the string or null when missing, null, or blank.
     */
    fun optional(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) {
            return null
        }
        return json.getString(key).takeIf { it.isNotBlank() }
    }
}
