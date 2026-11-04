package com.beamlink.peer.security

/**
 * Peer URL choice from the nexus peer list. Listed pin is https. Missing pin is refused.
 */
data class PeerTransport(
    val scheme: String,
    val port: Int,
    val pin: String,
) {

    class MissingPinException : IllegalStateException("Peer TLS pin missing")

    /**
     * scheme://ip:port with no path.
     */
    fun baseUrl(ip: String): String {
        return "$scheme://$ip:$port"
    }

    companion object {
        /**
         * https plus pin when tlsFingerprint is listed. Missing pin throws MissingPinException.
         */
        @Suppress("UNUSED_PARAMETER")
        fun of(tlsFingerprint: String?, tlsPort: Int?, httpPort: Int): PeerTransport {
            if (!tlsFingerprint.isNullOrBlank()) {
                if (tlsPort == null || tlsPort <= 0) {
                    throw IllegalStateException("Peer TLS pin listed but tlsPort missing")
                }
                return PeerTransport("https", tlsPort, tlsFingerprint)
            }
            throw MissingPinException()
        }
    }
}
