package com.atamanahmet.beamlink.peer.dto;

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
    private UUID deviceId;

    private String deviceName;
    private String ipAddress;
    private int port;
    private long lastSeen;
    private boolean online;
    private UUID publicId;
    private Integer tlsPort;
    private String tlsFingerprint;
}