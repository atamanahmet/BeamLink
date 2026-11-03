package com.atamanahmet.beamlink.peer.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DeviceRegistrationResponse {
    private UUID deviceId;
    private int interval;
}
