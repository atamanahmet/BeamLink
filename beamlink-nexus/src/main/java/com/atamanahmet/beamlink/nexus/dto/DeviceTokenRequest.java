package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DeviceTokenRequest {
    private UUID deviceId;
    private String challenge;
    private String signature;
    private String tlsFingerprint;
    private Integer tlsPort;
    private String nexusLeafSha256;
}
