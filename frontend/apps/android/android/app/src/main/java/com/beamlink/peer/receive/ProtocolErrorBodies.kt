package com.beamlink.peer.receive

import org.json.JSONObject
import java.util.UUID

/**
 * RFC 9457 JSON with the Java device field names.
 */
object ProtocolErrorBodies {

    private const val TYPE = "about:blank"
    private const val REASON = "UNKNOWN"

    /**
     * Builds RFC 9457 JSON for a protocol error.
     */
    fun toJson(error: ProtocolException): JSONObject {
        val title = if (error.code == "OFFSET_MISMATCH") {
            "Offset mismatch"
        } else {
            "Transfer protocol error"
        }
        val json = of(error.status, title, error.message, error.code)
        if (error.pausedBy != null) {
            json.put("pausedBy", error.pausedBy)
        }
        if (error.expectedOffset != null) {
            json.put("expectedOffset", error.expectedOffset)
        }
        return json
    }

    /**
     * Builds RFC 9457 JSON with title, detail, status, code, reason, and correlationId.
     */
    fun of(status: Int, title: String, detail: String, code: String): JSONObject {
        return JSONObject()
            .put("type", TYPE)
            .put("title", title)
            .put("status", status)
            .put("detail", detail)
            .put("code", code)
            .put("reason", REASON)
            .put("correlationId", UUID.randomUUID().toString())
    }
}
