package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordSetupRuleTest {

    @Test
    void setupOnlyOnce() {
        assertTrue(PasswordSetupRule.maySetup(false));
        assertFalse(PasswordSetupRule.maySetup(true));
    }
}
