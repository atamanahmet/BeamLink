package com.beamlink.peer.exception

import org.json.JSONException

/**
 * Maps a throwable to HTTP status, code, and problem+json content type.
 */
object ProblemStatusMapper {

    const val CONTENT_TYPE = "application/problem+json"

    data class Mapping(
        val status: Int,
        val code: String,
        val contentType: String,
    )

    /**
     * Picks status and code. ProtocolException is handled on its own path.
     */
    fun map(error: Throwable): Mapping {
        return when (error) {
            is InvalidRequestException -> Mapping(
                400,
                error.code.ifBlank { "INVALID_REQUEST" },
                CONTENT_TYPE,
            )
            is JSONException -> Mapping(400, "INVALID_REQUEST", CONTENT_TYPE)
            is IllegalArgumentException -> Mapping(500, "INTERNAL_ERROR", CONTENT_TYPE)
            else -> Mapping(500, "INTERNAL_ERROR", CONTENT_TYPE)
        }
    }
}
