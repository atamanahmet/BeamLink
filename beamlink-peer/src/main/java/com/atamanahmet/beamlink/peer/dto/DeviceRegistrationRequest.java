package com.atamanahmet.beamlink.peer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceRegistrationRequest {
    private String deviceName;
    private String ipAddress;
    private int port;
    private String publicKey;
    private String tlsFingerprint;
    private Integer tlsPort;
    private String signature;
}
