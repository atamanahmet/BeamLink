package com.atamanahmet.beamlink.peer.dto;

import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;

/**
 * Result of a re-register attempt.
 */
public record DeviceResetResponse(boolean nexusRemoved, DeviceState state, String message) {
}
