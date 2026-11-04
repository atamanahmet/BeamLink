package com.beamlink.peer.security

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Short probe error text from a throwable chain.
 */
object ProbeFailure {

    const val UNREACHABLE = "Nexus unreachable"
    const val CANNOT_CONNECT =
        "Cannot connect. Check the address, the port, and that nexus is running."
    const val TLS_ERROR = "TLS error"

    /**
     * Maps a throwable chain to a failure class. Never reads the message.
     */
    fun classify(error: Throwable): String {
        var current: Throwable? = error
        val seen = HashSet<Throwable>()
        for (i in 0 until 8) {
            if (current == null || !seen.add(current)) {
                break
            }
            when (current) {
                is SSLException, is CertificateException -> return TLS_ERROR
                is SocketTimeoutException, is UnknownHostException -> return UNREACHABLE
                is ConnectException -> return CANNOT_CONNECT
            }
            current = current.cause
        }
        return UNREACHABLE
    }

    /**
     * Maps connect, timeout, and TLS failures to a short line.
     */
    fun message(error: Throwable): String {
        return classify(error)
    }
}
