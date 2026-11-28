package com.beamlink.peer.exception

/**
 * Caller is not allowed to act on this transfer.
 */
class ForbiddenException(
    message: String = "Access denied",
    val code: String = "FORBIDDEN",
) : RuntimeException(message)
