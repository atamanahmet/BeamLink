package com.atamanahmet.beamlink.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class DeviceStatusDTO {
    private UUID deviceId;
    private String ipAddress;
    private int port;
    private long peerVersion;
}
