package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ApprovalPushRequest {
    private UUID agentId;
    private String approvedName;
    private String authToken;
    private String publicToken;
    private UUID publicId;
    private AgentState state;
    private String nexusPublicKey;
}