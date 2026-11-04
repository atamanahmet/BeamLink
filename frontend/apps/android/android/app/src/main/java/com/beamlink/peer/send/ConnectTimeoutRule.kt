package com.beamlink.peer.send

import java.net.SocketTimeoutException

/**
 * Connect timeout is a SocketTimeoutException during the connect phase.
 */
object ConnectTimeoutRule {

    /**
     * True when the failure is a connect-phase SocketTimeoutException.
     */
    fun isConnectTimeout(error: Throwable, connectPhase: Boolean): Boolean {
        if (!connectPhase) {
            return false
        }
        var current: Throwable? = error
        for (i in 0 until 10) {
            if (current == null) {
                break
            }
            if (current is SocketTimeoutException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
