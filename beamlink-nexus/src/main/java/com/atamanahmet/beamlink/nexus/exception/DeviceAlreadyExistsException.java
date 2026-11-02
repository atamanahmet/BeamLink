package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import lombok.Getter;

import java.util.UUID;

@Getter
public class DeviceAlreadyExistsException extends RuntimeException {
    private final UUID deviceId;
    private final DeviceState state;

    public DeviceAlreadyExistsException(UUID deviceId, DeviceState state) {
        super("Device already exists: " + deviceId);
        this.deviceId = deviceId;
        this.state = state;
    }
}