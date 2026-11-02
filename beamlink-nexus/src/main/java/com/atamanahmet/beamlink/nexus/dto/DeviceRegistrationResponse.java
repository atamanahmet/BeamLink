package com.atamanahmet.beamlink.nexus.dto;

import java.util.UUID;

public record DeviceRegistrationResponse(UUID deviceId, int interval) {
}
