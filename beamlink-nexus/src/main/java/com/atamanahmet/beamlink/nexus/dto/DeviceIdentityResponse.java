package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class DeviceIdentityResponse {
    private UUID deviceId;
    private String deviceName;
    private DeviceState state;
    private UUID publicId;
    private String nexusPublicKey;
}
