package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.config.RsaKeyConfig;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.service.DeviceEnrollmentService;
import com.atamanahmet.beamlink.nexus.service.EnrollmentThrottle;
import com.atamanahmet.beamlink.nexus.service.NexusResumePolicyStore;
import com.atamanahmet.beamlink.nexus.websocket.DeviceWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Identify keeps nexusPublicKey when approved and never includes token fields.
 */
class DeviceIdentifyPublicKeyTest {

    @Test
    void identifyAddsPublicKeyWhenApproved() throws Exception {
        DeviceService deviceService = mock(DeviceService.class);
        NexusResumePolicyStore resume = mock(NexusResumePolicyStore.class);
        RsaKeyConfig rsa = mock(RsaKeyConfig.class);
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID publicId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Device device = Device.builder()
                .id(id)
                .name("phone")
                .ipAddress("10.0.0.8")
                .port(9090)
                .state(DeviceState.APPROVED)
                .publicId(publicId)
                .build();
        when(deviceService.getByIpAddressAndPort("10.0.0.8", 9090)).thenReturn(Optional.of(device));
        when(rsa.getPublicKeyBase64()).thenReturn("nexus-pk");

        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getRemoteAddr()).thenReturn("10.0.0.8");

        DeviceWebSocketHandler ws = mock(DeviceWebSocketHandler.class);
        DeviceEnrollmentService enrollment = mock(DeviceEnrollmentService.class);
        EnrollmentThrottle throttle = mock(EnrollmentThrottle.class);
        DeviceController controller = new DeviceController(
                deviceService, enrollment, throttle, resume, rsa, ws);
        ResponseEntity<?> response = controller.identify("10.0.0.8", 9090, http);

        assertThat(response.getBody()).isInstanceOf(
                com.atamanahmet.beamlink.nexus.dto.DeviceIdentityResponse.class);
        com.atamanahmet.beamlink.nexus.dto.DeviceIdentityResponse body =
                (com.atamanahmet.beamlink.nexus.dto.DeviceIdentityResponse) response.getBody();
        assertThat(body.getNexusPublicKey()).isEqualTo("nexus-pk");
        String json = new ObjectMapper().writeValueAsString(body);
        assertThat(json).doesNotContain("authToken");
        assertThat(json).doesNotContain("publicToken");
    }
}
