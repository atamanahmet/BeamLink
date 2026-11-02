package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EraseAllCallerRuleTest {

    @Test
    void allowsLoopbackWithMatchingSecret() {
        assertThat(EraseAllCallerRule.allowed("127.0.0.1", "secret", "secret")).isTrue();
        assertThat(EraseAllCallerRule.allowed("::1", "secret", "secret")).isTrue();
    }

    @Test
    void deniesLanWrongSecretOrBlankExpected() {
        assertThat(EraseAllCallerRule.allowed("10.0.0.8", "secret", "secret")).isFalse();
        assertThat(EraseAllCallerRule.allowed("127.0.0.1", "wrong", "secret")).isFalse();
        assertThat(EraseAllCallerRule.allowed("127.0.0.1", "secret", "")).isFalse();
        assertThat(EraseAllCallerRule.allowed("127.0.0.1", "secret", null)).isFalse();
        assertThat(EraseAllCallerRule.allowed("127.0.0.1", null, "secret")).isFalse();
    }
}
