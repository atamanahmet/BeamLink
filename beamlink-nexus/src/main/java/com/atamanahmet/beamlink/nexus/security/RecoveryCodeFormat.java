package com.atamanahmet.beamlink.nexus.security;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Formats and normalizes the admin recovery code.
 */
public final class RecoveryCodeFormat {

    private static final int RAW_BYTES = 16;

    private RecoveryCodeFormat() {
    }

    /**
     * Strips dashes and spaces, then lowercases.
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Groups hex in fours split by dashes.
     */
    public static String formatDisplay(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) {
                grouped.append('-');
            }
            int end = Math.min(i + 4, hex.length());
            grouped.append(hex, i, end);
        }
        return grouped.toString();
    }

    /**
     * 128 random bits as lowercase hex.
     */
    public static String newHex(SecureRandom random) {
        byte[] raw = new byte[RAW_BYTES];
        random.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }
}
