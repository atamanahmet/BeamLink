package com.beamlink.peer.send

import java.net.InetAddress

/**
 * User text for a connect timeout to a non-loopback host.
 */
object ConnectTimeoutText {

    const val MESSAGE =
        "Could not reach the other device. If it runs Windows, its firewall may be blocking BeamLink. Allow BeamLink on Private networks (needs an admin once)."

    /**
     * Firewall sentence for a connect-phase timeout, else null.
     */
    fun of(error: Throwable, host: String?): String? {
        if (isLoopback(host) || !isTypedConnectTimeout(error)) {
            return null
        }
        return MESSAGE
    }

    /**
     * True when the chain contains ConnectTimeoutException.
     */
    private fun isTypedConnectTimeout(error: Throwable): Boolean {
        var current: Throwable? = error
        for (i in 0 until 10) {
            if (current == null) {
                break
            }
            if (current is ConnectTimeoutException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * True for localhost, 127.x, and ::1.
     */
    private fun isLoopback(host: String?): Boolean {
        if (host.isNullOrBlank()) {
            return false
        }
        return try {
            InetAddress.getByName(host).isLoopbackAddress
        } catch (_: Exception) {
            false
        }
    }
}
