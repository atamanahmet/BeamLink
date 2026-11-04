package com.beamlink.peer.exception

/**
 * Client input that should be 400 INVALID_REQUEST.
 */
class InvalidRequestException : RuntimeException {
    val code: String

    constructor(message: String) : this(message, "INVALID_REQUEST")

    constructor(message: String, code: String) : super(message) {
        this.code = code
    }

    constructor(message: String, cause: Throwable) : super(message, cause) {
        this.code = "INVALID_REQUEST"
    }
}
