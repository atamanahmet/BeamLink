package com.atamanahmet.beamlink.agent.mapper;

import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.dto.PeerDTO;

public final class PeerMapper {

    private PeerMapper() {}

    /** Creates a new entity from inbound Nexus peer data, ready to persist. */
    public static Peer toEntity(PeerDTO dto) {
        return Peer.builder()
                .agentId(dto.getAgentId())
                .agentName(dto.getAgentName())
                .ipAddress(dto.getIpAddress())
                .port(dto.getPort())
                .lastSeen(dto.getLastSeen() != 0 ? dto.getLastSeen() : System.currentTimeMillis())
                .online(dto.isOnline())
                .publicId(dto.getPublicId())
                .build();
    }

    /** Maps entity to DTO when needed for outbound use. */
    public static PeerDTO toDTO(Peer entity) {
        return PeerDTO.builder()
                .agentId(entity.getAgentId())
                .agentName(entity.getAgentName())
                .ipAddress(entity.getIpAddress())
                .port(entity.getPort())
                .lastSeen(entity.getLastSeen())
                .online(entity.isOnline())
                .publicId(entity.getPublicId())
                .build();
    }
}