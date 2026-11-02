package com.atamanahmet.beamlink.nexus.security;

/**
 * Gates first-run password setup to a single use.
 */
public final class PasswordSetupRule {

    private PasswordSetupRule() {
    }

    /**
     * Returns true when setup is still allowed.
     */
    public static boolean maySetup(boolean passwordExists) {
        return !passwordExists;
    }
}
