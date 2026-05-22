package com.atamanahmet.beamlink.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeerDTO {

    @JsonAlias("id")
    private UUID agentId;

    private String agentName;
    private String ipAddress;
    private int port;
    private long lastSeen;
    private boolean online;
    private UUID publicId;
}