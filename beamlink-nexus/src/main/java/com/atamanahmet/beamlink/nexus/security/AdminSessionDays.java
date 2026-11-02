package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;

import java.util.Set;

/**
 * Maps login sessionDays values to JWT expiry days.
 */
public final class AdminSessionDays {

    public static final String DEFAULT = "1";

    private static final Set<String> ALLOWED = Set.of("1", "7", "30", "365");

    private AdminSessionDays() {
    }

    /**
     * Returns true when sessionDays is in the login whitelist.
     */
    public static boolean isAllowed(String sessionDays) {
        return sessionDays != null && ALLOWED.contains(sessionDays);
    }

    /**
     * Normalizes blank to default 1. Rejects other values.
     */
    public static String normalize(String sessionDays) {
        if (sessionDays == null || sessionDays.isBlank()) {
            return DEFAULT;
        }
        if (!isAllowed(sessionDays)) {
            throw new InvalidRequestException("Invalid sessionDays");
        }
        return sessionDays;
    }

    /**
     * Returns expiry days for the session length.
     */
    public static int toExpirationDays(String sessionDays) {
        return Integer.parseInt(normalize(sessionDays));
    }
}
