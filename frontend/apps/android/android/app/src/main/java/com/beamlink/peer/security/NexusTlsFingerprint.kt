package com.beamlink.peer.security

import java.security.cert.X509Certificate

/**
 * Leaf cert SHA-256, hex, first 32 chars, groups of 4.
 */
object NexusTlsFingerprint {

    /**
     * Formats the leaf using the D137 fingerprint helper.
     */
    fun of(leaf: X509Certificate): String {
        return DeviceKey.fingerprint(leaf.encoded)
    }
}
