package com.beamlink.peer.security

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * Full SHA-256 of a TLS leaf. Canonical is 64 lowercase hex.
 */
object NexusTlsFingerprint {

    private const val HEX_LEN = 64

    /**
     * SHA-256 of the leaf encoding as 64 lowercase hex.
     */
    fun of(leaf: X509Certificate): String {
        return sha256Hex(leaf.encoded)
    }

    /**
     * Strips spaces and colons, lowercases, requires exactly 64 hex.
     */
    fun canonicalize(input: String?): String {
        if (input == null) {
            throw IllegalArgumentException("TLS pin must be 64 hex characters")
        }
        val hex = buildString(HEX_LEN) {
            for (c in input) {
                if (c != ' ' && c != ':') {
                    append(c)
                }
            }
        }.lowercase(Locale.ROOT)
        if (hex.length != HEX_LEN || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            throw IllegalArgumentException("TLS pin must be 64 hex characters")
        }
        return hex
    }

    /**
     * Four lines of 16 hex for UI.
     */
    fun format(pin: String): String {
        val hex = canonicalize(pin)
        return hex.substring(0, 16) + "\n" +
            hex.substring(16, 32) + "\n" +
            hex.substring(32, 48) + "\n" +
            hex.substring(48, 64)
    }

    private fun sha256Hex(der: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(der)
        return hash.joinToString("") { b -> "%02x".format(b) }
    }
}
