package com.atamanahmet.beamlink.peer.dto;

import lombok.Builder;

@Builder
public record StatusUpdatePayload(String ipAddress, int port, long peerVersion) {}