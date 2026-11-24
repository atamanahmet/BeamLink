package com.atamanahmet.beamlink.peer.security;

import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Locale;

/**
 * Full SHA-256 of a TLS leaf. Canonical is 64 lowercase hex.
 */
public final class NexusTlsFingerprint {

    private static final int HEX_LEN = 64;

    private NexusTlsFingerprint() {
    }

    /**
     * SHA-256 of the leaf encoding as 64 lowercase hex.
     */
    public static String of(X509Certificate leaf) {
        try {
            return sha256Hex(leaf.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("leaf encode failed", e);
        }
    }

    /**
     * Strips spaces and colons, lowercases, requires exactly 64 hex.
     */
    public static String canonicalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("TLS pin must be 64 hex characters");
        }
        StringBuilder out = new StringBuilder(HEX_LEN);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ' || c == ':') {
                continue;
            }
            out.append(c);
        }
        String hex = out.toString().toLowerCase(Locale.ROOT);
        if (hex.length() != HEX_LEN || !hex.chars().allMatch(NexusTlsFingerprint::isHex)) {
            throw new IllegalArgumentException("TLS pin must be 64 hex characters");
        }
        return hex;
    }

    /**
     * Four lines of 16 hex for UI.
     */
    public static String format(String pin) {
        String hex = canonicalize(pin);
        return hex.substring(0, 16) + "\n"
                + hex.substring(16, 32) + "\n"
                + hex.substring(32, 48) + "\n"
                + hex.substring(48, 64);
    }

    private static String sha256Hex(byte[] der) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(der);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("fingerprint failed", e);
        }
    }

    private static boolean isHex(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
