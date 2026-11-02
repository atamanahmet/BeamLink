package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeviceRegistrationRequest {
    private String deviceName;
    private String ipAddress;
    private int port;
    private String publicKey;
    private String tlsFingerprint;
    private Integer tlsPort;
    private String signature;
}
