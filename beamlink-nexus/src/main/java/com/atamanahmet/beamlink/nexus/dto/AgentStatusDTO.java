package com.atamanahmet.beamlink.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class AgentStatusDTO {
    private UUID agentId;
    private String ipAddress;
    private int port;
    private long peerVersion;
}
