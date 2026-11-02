package com.atamanahmet.beamlink.nexus.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Allows Erase all only from loopback with a matching UI secret.
 */
public final class EraseAllCallerRule {

    private EraseAllCallerRule() {
    }

    /**
     * True when the caller is loopback and the secret matches.
     */
    public static boolean allowed(String remoteAddr, String providedSecret, String expectedSecret) {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            return false;
        }
        return LoopbackAddresses.isLoopback(remoteAddr)
                && secretsEqual(providedSecret, expectedSecret);
    }

    /**
     * Compares secrets in constant time.
     */
    static boolean secretsEqual(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        byte[] a = provided.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
