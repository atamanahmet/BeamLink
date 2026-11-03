package com.atamanahmet.beamlink.peer.security.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigPublicMatchersTest {

    @Test
    void eventsIsNotPublic() {
        assertFalse(
                Arrays.asList(SecurityConfig.PUBLIC_MATCHERS).contains("/api/device/events")
        );
    }

    @Test
    void healthStaysPublic() {
        assertTrue(Arrays.asList(SecurityConfig.PUBLIC_MATCHERS).contains("/actuator/health"));
        assertFalse(Arrays.asList(SecurityConfig.PUBLIC_MATCHERS).contains("/api/approval"));
    }
}
