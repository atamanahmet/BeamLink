package com.atamanahmet.beamlink.peer.security;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * Leaf cert SHA-256, hex, first 32 chars, groups of 4.
 */
public final class NexusTlsFingerprint {

    private NexusTlsFingerprint() {
    }

    /**
     * Formats the leaf using the D137 fingerprint helper.
     */
    public static String of(X509Certificate leaf) {
        try {
            return DeviceKey.fingerprint(leaf.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("leaf encode failed", e);
        }
    }
}
