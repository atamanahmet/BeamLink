package com.atamanahmet.beamlink.peer.dto;

import java.util.List;

public record StatusUpdateResponse(
        String status,
        boolean peerOutdated,
        List<PeerDTO> peers
) {}
