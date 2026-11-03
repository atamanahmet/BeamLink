package com.atamanahmet.beamlink.peer.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceUiSecretFilterTest {

    @Test
    void acceptsMatchingSecret() {
        assertTrue(DeviceUiSecretFilter.secretsEqual("abc", "abc"));
    }

    @Test
    void rejectsMissingOrWrongSecret() {
        assertFalse(DeviceUiSecretFilter.secretsEqual(null, "abc"));
        assertFalse(DeviceUiSecretFilter.secretsEqual("abc", null));
        assertFalse(DeviceUiSecretFilter.secretsEqual("wrong", "abc"));
        assertFalse(DeviceUiSecretFilter.secretsEqual("", "abc"));
    }
}
