package com.atamanahmet.beamlink.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Statistics for dashboard
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NexusStats {

    private DeviceStats deviceStats;
    private TransferStats transferStats;
    private String tlsFingerprint;
}