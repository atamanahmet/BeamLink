package com.atamanahmet.beamlink.agent.dto;

import java.util.List;

public record StatusUpdateResponse(
        String status,
        boolean peerOutdated,
        List<PeerDTO> peers
) {}
