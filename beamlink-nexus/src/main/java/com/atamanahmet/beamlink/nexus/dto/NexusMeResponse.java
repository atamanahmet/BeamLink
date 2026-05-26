package com.atamanahmet.beamlink.nexus.dto;

import java.util.UUID;

public record NexusMeResponse(
        UUID nexusId,
        String nexusName,
        String ipAddress,
        Integer port,
        String username
) {}