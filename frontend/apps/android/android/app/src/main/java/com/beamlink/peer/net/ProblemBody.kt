package com.beamlink.peer.net

import com.beamlink.peer.send.ConnectTimeoutText
import org.json.JSONObject

/**
 * RFC 9457 fields from a body string. Never throws.
 */
data class ProblemBody(
    val title: String?,
    val detail: String?,
    val code: String?,
    val reason: String?,
    val correlationId: String?,
    val status: Int,
) {
    companion object {
        /**
         * Reads title, detail, code, reason, and correlationId. Bad JSON stays empty.
         */
        fun parse(body: String?, status: Int): ProblemBody {
            if (body.isNullOrBlank()) {
                return empty(status)
            }
            return try {
                val json = JSONObject(body)
                ProblemBody(
                    title = text(json, "title"),
                    detail = text(json, "detail"),
                    code = text(json, "code"),
                    reason = text(json, "reason"),
                    correlationId = text(json, "correlationId"),
                    status = status,
                )
            } catch (_: Exception) {
                empty(status)
            }
        }

        /**
         * CONNECT_TIMEOUT uses the firewall sentence. Other bodies keep detail.
         */
        fun userText(body: String?, status: Int): String {
            val problem = parse(body, status)
            val text = if (problem.reason == "CONNECT_TIMEOUT") {
                ConnectTimeoutText.MESSAGE
            } else {
                problem.detail ?: "Request failed ($status)"
            }
            val ref = problem.correlationId
            return if (ref.isNullOrBlank()) {
                text
            } else {
                "$text\nRef: $ref"
            }
        }

        private fun empty(status: Int): ProblemBody {
            return ProblemBody(null, null, null, null, null, status)
        }

        private fun text(json: JSONObject, key: String): String? {
            if (!json.has(key) || json.isNull(key)) {
                return null
            }
            return json.optString(key, "").takeIf { it.isNotBlank() }
        }
    }
}
