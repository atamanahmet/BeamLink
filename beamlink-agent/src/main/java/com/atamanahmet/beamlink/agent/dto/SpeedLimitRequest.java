package com.atamanahmet.beamlink.agent.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for per-transfer or settings speed limit updates.
 */
@Getter
@Setter
@NoArgsConstructor
public class SpeedLimitRequest {

    private Double limitMbps;
}
