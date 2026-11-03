package com.atamanahmet.beamlink.peer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceIpAddressChooseTest {

    @Test
    void sameKeepsStored() {
        assertThat(DeviceService.chooseIpAddress("10.0.0.1", "10.0.0.1"))
                .isEqualTo("10.0.0.1");
    }

    @Test
    void changedUsesDetected() {
        assertThat(DeviceService.chooseIpAddress("10.0.0.1", "10.0.0.2"))
                .isEqualTo("10.0.0.2");
    }

    @Test
    void nullOrEmptyKeepsStored() {
        assertThat(DeviceService.chooseIpAddress("10.0.0.1", null))
                .isEqualTo("10.0.0.1");
        assertThat(DeviceService.chooseIpAddress("10.0.0.1", ""))
                .isEqualTo("10.0.0.1");
        assertThat(DeviceService.chooseIpAddress("10.0.0.1", "   "))
                .isEqualTo("10.0.0.1");
    }
}
