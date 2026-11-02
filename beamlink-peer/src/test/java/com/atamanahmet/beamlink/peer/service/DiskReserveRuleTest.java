package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiskReserveRuleTest {

    @Test
    void exactBoundaryIsAllowed() {
        assertThat(DiskReserveRule.allowed(200L, 50L, 150L)).isTrue();
    }

    @Test
    void oneByteOverIsRefused() {
        assertThat(DiskReserveRule.allowed(200L, 51L, 150L)).isFalse();
    }

    @Test
    void sizeZeroStillNeedsReserve() {
        assertThat(DiskReserveRule.allowed(150L, 0L, 150L)).isTrue();
        assertThat(DiskReserveRule.allowed(149L, 0L, 150L)).isFalse();
    }

    @Test
    void reserveBiggerThanDiskIsRefused() {
        assertThat(DiskReserveRule.allowed(100L, 0L, 200L)).isFalse();
    }
}
