package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.dto.NexusStats;

import com.atamanahmet.beamlink.nexus.dto.DeviceStats;
import com.atamanahmet.beamlink.nexus.dto.TransferStats;
import com.atamanahmet.beamlink.nexus.security.NexusTlsCert;
import com.atamanahmet.beamlink.nexus.security.NexusTlsFingerprint;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * Calculates statistics for dashboard
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final DeviceService deviceService;
    private final TransferLogService transferLogService;
    private final NexusTlsCert nexusTlsCert;

    /**
     * Get current nexus statistics
     */
    public NexusStats getStats() {

        DeviceStats deviceStats = deviceService.getDeviceStats();
        TransferStats transferStats = transferLogService.getTransferStats();

        return new NexusStats(
                deviceStats,
                transferStats,
                NexusTlsFingerprint.of(nexusTlsCert.getLeaf())
        );
    }
}