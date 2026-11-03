package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MdnsDiscoveryRuleTest {

    @Test
    void suggestionStopsScan() {
        assertThat(MdnsDiscoveryRule.stopScan(true, false)).isTrue();
    }

    @Test
    void storedUrlStopsScan() {
        assertThat(MdnsDiscoveryRule.stopScan(false, true)).isTrue();
    }

    @Test
    void nothingReadyKeepsScan() {
        assertThat(MdnsDiscoveryRule.stopScan(false, false)).isFalse();
    }
}
