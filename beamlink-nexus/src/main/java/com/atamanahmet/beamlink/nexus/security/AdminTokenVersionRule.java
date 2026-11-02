package com.atamanahmet.beamlink.nexus.security;

/**
 * Checks admin JWT tv claim against the Nexus row version.
 */
public final class AdminTokenVersionRule {

    private AdminTokenVersionRule() {
    }

    /**
     * Returns true when the claim matches the current database version.
     */
    public static boolean matches(Integer claimTv, int dbTv) {
        return claimTv != null && claimTv == dbTv;
    }
}
