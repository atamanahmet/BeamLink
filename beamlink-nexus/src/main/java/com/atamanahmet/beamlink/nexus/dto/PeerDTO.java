package com.atamanahmet.beamlink.nexus.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PeerDTO {
    private UUID id;
    private String deviceName;
    private String ipAddress;
    private int port;
    private boolean online;
    private UUID publicId;
    private long lastSeen;
    private Integer tlsPort;
    private String tlsFingerprint;
}
