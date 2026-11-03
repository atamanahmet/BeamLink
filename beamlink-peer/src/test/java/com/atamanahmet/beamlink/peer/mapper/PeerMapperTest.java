package com.atamanahmet.beamlink.peer.mapper;

import com.atamanahmet.beamlink.peer.domain.Peer;
import com.atamanahmet.beamlink.peer.dto.PeerSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PeerMapperTest {

    @Test
    void toSummaryUsesDeviceIdAsId() {
        UUID deviceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Peer entity = Peer.builder()
                .deviceId(deviceId)
                .deviceName("peer-a")
                .ipAddress("10.0.0.2")
                .port(8080)
                .lastSeen(1L)
                .online(true)
                .publicId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .build();

        PeerSummaryResponse summary = PeerMapper.toSummary(entity);

        assertThat(summary.id()).isEqualTo(deviceId);
        assertThat(summary.deviceName()).isEqualTo("peer-a");
        assertThat(summary.ipAddress()).isEqualTo("10.0.0.2");
        assertThat(summary.port()).isEqualTo(8080);
        assertThat(summary.online()).isTrue();
    }
}
