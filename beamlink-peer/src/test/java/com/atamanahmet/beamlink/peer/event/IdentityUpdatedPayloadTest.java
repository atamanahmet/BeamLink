package com.atamanahmet.beamlink.peer.event;

import com.atamanahmet.beamlink.peer.domain.Device;
import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUpdatedPayloadTest {

    @Test
    void identityUpdatedHasNoTokens() {
        Device device = new Device();
        device.setId(99L);
        device.setDeviceId(UUID.fromString("11111111-2222-4333-8444-555555555555"));
        device.setDeviceName("desk");
        device.setState(DeviceState.APPROVED);
        device.setPublicToken("public-secret");
        device.setAuthToken("auth-secret");

        Map<String, String> payload = DeviceIdentityEventListener.identityUpdatedPayload(device);

        assertThat(payload).doesNotContainKeys("publicToken", "authToken");
        assertThat(payload).containsOnlyKeys("deviceId", "deviceName", "state");
        assertThat(payload.get("deviceId")).isEqualTo("11111111-2222-4333-8444-555555555555");
        assertThat(payload.get("deviceId")).isNotEqualTo("99");
        assertThat(payload.get("deviceName")).isEqualTo("desk");
        assertThat(payload.get("state")).isEqualTo("APPROVED");
    }
}
