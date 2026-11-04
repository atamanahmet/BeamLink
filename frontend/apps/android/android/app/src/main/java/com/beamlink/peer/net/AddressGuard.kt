package com.beamlink.peer.net

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Allows only local and private HTTP hosts for nexus and peer calls.
 */
object AddressGuard {

    /**
     * Validates https://host:port for the nexus URL.
     */
    fun requireAllowedNexusUrl(raw: String): URI {
        return requireAllowedUrl(raw, "https")
    }

    /**
     * Validates http://host:port. Throws IllegalArgumentException with a clear message.
     */
    fun requireAllowedHttpUrl(raw: String): URI {
        return requireAllowedUrl(raw, "http")
    }

    /**
     * Validates http or https peer URL. Pin choice lives in PeerTransport.
     */
    fun requireAllowedPeerUrl(raw: String): URI {
        return requireAllowedUrl(raw, "http", "https")
    }

    private fun requireAllowedUrl(raw: String, vararg schemes: String): URI {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Nexus address is empty")
        }
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid address: $trimmed", e)
        }
        val got = uri.scheme?.lowercase(Locale.US)
        if (got == null || schemes.none { it == got }) {
            throw IllegalArgumentException(
                "Only ${schemes.joinToString(" or ") { "$it://" }} addresses are allowed",
            )
        }
        val host = uri.host
        if (host.isNullOrBlank()) {
            throw IllegalArgumentException("Address must include a host")
        }
        if (uri.port < 0) {
            throw IllegalArgumentException("Address must include a port")
        }
        requireAllowedHost(host)
        return uri
    }

    /**
     * Validates a peer host or IP before any outbound request.
     */
    fun requireAllowedHost(host: String) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Host is empty")
        }
        if (trimmed.lowercase(Locale.US).endsWith(".local")) {
            return
        }
        val address = try {
            InetAddress.getByName(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot resolve host: $trimmed", e)
        }
        if (isAllowedAddress(address)) {
            return
        }
        throw IllegalArgumentException(
            "Host refused: $trimmed. Only loopback, private IPv4, link-local, or .local is allowed",
        )
    }

    /**
     * True for loopback, link-local, or private IPv4 ranges.
     */
    fun isAllowedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes.size != 4) {
            return false
        }
        val b0 = bytes[0].toInt() and 0xff
        val b1 = bytes[1].toInt() and 0xff
        if (b0 == 10) {
            return true
        }
        if (b0 == 192 && b1 == 168) {
            return true
        }
        if (b0 == 172 && b1 in 16..31) {
            return true
        }
        return false
    }
}
