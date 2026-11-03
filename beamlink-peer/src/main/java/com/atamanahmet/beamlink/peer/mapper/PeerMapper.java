package com.atamanahmet.beamlink.peer.mapper;

import com.atamanahmet.beamlink.peer.domain.Peer;
import com.atamanahmet.beamlink.peer.dto.PeerDTO;
import com.atamanahmet.beamlink.peer.dto.PeerSummaryResponse;

public final class PeerMapper {

    private PeerMapper() {}

    /** Creates a new entity from inbound Nexus peer data, ready to persist. */
    public static Peer toEntity(PeerDTO dto) {
        return Peer.builder()
                .deviceId(dto.getDeviceId())
                .deviceName(dto.getDeviceName())
                .ipAddress(dto.getIpAddress())
                .port(dto.getPort())
                .lastSeen(dto.getLastSeen() != 0 ? dto.getLastSeen() : System.currentTimeMillis())
                .online(dto.isOnline())
                .publicId(dto.getPublicId())
                .tlsPort(dto.getTlsPort())
                .tlsFingerprint(dto.getTlsFingerprint())
                .build();
    }

    /** Maps entity to DTO when needed for outbound use. */
    public static PeerDTO toDTO(Peer entity) {
        return PeerDTO.builder()
                .deviceId(entity.getDeviceId())
                .deviceName(entity.getDeviceName())
                .ipAddress(entity.getIpAddress())
                .port(entity.getPort())
                .lastSeen(entity.getLastSeen())
                .online(entity.isOnline())
                .publicId(entity.getPublicId())
                .tlsPort(entity.getTlsPort())
                .tlsFingerprint(entity.getTlsFingerprint())
                .build();
    }

    /**
     * Maps entity to REST peer summary. id comes from deviceId.
     */
    public static PeerSummaryResponse toSummary(Peer entity) {
        return new PeerSummaryResponse(
                entity.getDeviceId(),
                entity.getDeviceName(),
                entity.getIpAddress(),
                entity.getPort(),
                entity.isOnline());
    }
}