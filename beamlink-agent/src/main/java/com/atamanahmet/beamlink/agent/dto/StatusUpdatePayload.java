package com.atamanahmet.beamlink.agent.dto;

import lombok.Builder;

@Builder
public record StatusUpdatePayload(String ipAddress, int port, long peerVersion) {}