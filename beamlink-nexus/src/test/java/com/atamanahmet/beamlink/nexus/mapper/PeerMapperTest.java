package com.atamanahmet.beamlink.nexus.mapper;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerSummaryResponse;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.atamanahmet.beamlink.nexus.security.NexusTlsCert;
import com.atamanahmet.beamlink.nexus.security.NexusTlsFingerprint;
import com.atamanahmet.beamlink.nexus.security.PeerTransport;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PeerMapperTest {

    @Test
    void toSummaryMapsFieldsAndHasNoToken() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Device device = Device.builder()
                .id(id)
                .name("peer-a")
                .ipAddress("10.0.0.2")
                .port(8080)
                .state(DeviceState.APPROVED)
                .lastSeenAt(Instant.now())
                .build();

        PeerSummaryResponse summary = PeerMapper.toSummary(device);

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.deviceName()).isEqualTo("peer-a");
        assertThat(summary.ipAddress()).isEqualTo("10.0.0.2");
        assertThat(summary.port()).isEqualTo(8080);
        assertThat(summary.online()).isTrue();
        assertThat(summary.tlsPort()).isNull();
        assertThat(summary.tlsFingerprint()).isNull();
        assertThat(Arrays.stream(PeerSummaryResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(n -> n.toLowerCase().contains("token"))).isFalse();
    }

    @Test
    void fromDeviceHasNoTokenFieldAndDoesNotCallTokenGenerator() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID publicId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Device device = Device.builder()
                .id(id)
                .name("peer-a")
                .ipAddress("10.0.0.2")
                .port(8080)
                .state(DeviceState.APPROVED)
                .publicId(publicId)
                .lastSeenAt(Instant.now())
                .build();

        NexusConfig nexusConfig = mock(NexusConfig.class);
        NexusService nexusService = mock(NexusService.class);
        DeviceTokenService tokenService = mock(DeviceTokenService.class);
        PeerMapper mapper = new PeerMapper(nexusConfig, nexusService, mock(NexusTlsCert.class));

        PeerDTO dto = mapper.fromDevice(device);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getDeviceName()).isEqualTo("peer-a");
        assertThat(dto.getIpAddress()).isEqualTo("10.0.0.2");
        assertThat(dto.getPort()).isEqualTo(8080);
        assertThat(dto.isOnline()).isTrue();
        assertThat(dto.getPublicId()).isEqualTo(publicId);
        assertThat(Arrays.stream(PeerDTO.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(n -> n.toLowerCase().contains("token"))).isFalse();
        verifyNoInteractions(tokenService);
        verify(tokenService, never()).generatePublicToken(id, publicId);
        verifyNoInteractions(nexusConfig);
        verifyNoInteractions(nexusService);
    }

    @Test
    void nexusPeerListsTlsSoPeerTransportPicksHttps() throws Exception {
        UUID nexusId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        X509Certificate leaf = mock(X509Certificate.class);
        when(leaf.getEncoded()).thenReturn(new byte[32]);
        NexusTlsCert cert = mock(NexusTlsCert.class);
        when(cert.getLeaf()).thenReturn(leaf);
        NexusConfig nexusConfig = mock(NexusConfig.class);
        when(nexusConfig.getName()).thenReturn("nexus");
        when(nexusConfig.getIp()).thenReturn("10.0.0.1");
        when(nexusConfig.getNexusPort()).thenReturn(7472);
        when(nexusConfig.getTlsPort()).thenReturn(7473);
        NexusService nexusService = mock(NexusService.class);
        when(nexusService.getNexusId()).thenReturn(nexusId);

        PeerMapper mapper = new PeerMapper(nexusConfig, nexusService, cert);
        PeerDTO dto = mapper.nexusPeer();
        PeerSummaryResponse summary = mapper.nexusPeerSummary();
        String pin = NexusTlsFingerprint.of(leaf);
        PeerTransport transport = PeerTransport.of(dto.getTlsFingerprint(), dto.getTlsPort(), dto.getPort());

        assertThat(dto.getPort()).isEqualTo(7472);
        assertThat(dto.getTlsPort()).isEqualTo(7473);
        assertThat(dto.getTlsFingerprint()).isEqualTo(pin);
        assertThat(summary.tlsPort()).isEqualTo(7473);
        assertThat(summary.tlsFingerprint()).isEqualTo(pin);
        assertThat(transport.scheme()).isEqualTo("https");
        assertThat(transport.port()).isEqualTo(7473);
        assertThat(transport.pin()).isEqualTo(pin);
        assertThat(transport.baseUrl("10.0.0.1")).isEqualTo("https://10.0.0.1:7473");
    }
}
