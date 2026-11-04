package com.beamlink.peer.receive

/**
 * HTTP protocol error with RFC 9457 JSON body.
 */
class ProtocolException(
    val code: String,
    override val message: String,
    val status: Int,
    val pausedBy: String? = null,
    val expectedOffset: Long? = null,
) : Exception(message)
