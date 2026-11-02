package com.atamanahmet.beamlink.nexus.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Chunk SHA-256 helpers for transfer integrity checks.
 */
public final class FileChecksum {

    private FileChecksum() {}

    /**
     * Computes lowercase hex SHA-256 of the given chunk bytes.
     */
    public static String sha256Hex(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("chunk bytes required");
        }
        return sha256Hex(data, 0, data.length);
    }

    /**
     * Computes lowercase hex SHA-256 of a slice of chunk bytes.
     */
    public static String sha256Hex(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("chunk bytes required");
        }
        MessageDigest digest = sha256Digest();
        digest.update(data, offset, length);
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Constant-time hex compare; case-insensitive.
     */
    public static boolean matches(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(expectedHex.trim());
            byte[] actual = HexFormat.of().parseHex(actualHex.trim());
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
