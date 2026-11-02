package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminTokenVersionRuleTest {

    @Test
    void tvMismatchRejected() {
        assertFalse(AdminTokenVersionRule.matches(0, 1));
        assertFalse(AdminTokenVersionRule.matches(2, 1));
        assertFalse(AdminTokenVersionRule.matches(null, 0));
    }

    @Test
    void tvMatchAccepted() {
        assertTrue(AdminTokenVersionRule.matches(0, 0));
        assertTrue(AdminTokenVersionRule.matches(3, 3));
    }
}
