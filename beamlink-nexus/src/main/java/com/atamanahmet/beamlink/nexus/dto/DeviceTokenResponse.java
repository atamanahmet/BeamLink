package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class DeviceTokenResponse {
    private UUID deviceId;
    private String approvedName;
    private String authToken;
    private String publicToken;
    private UUID publicId;
    private DeviceState state;
    private String nexusPublicKey;
}
