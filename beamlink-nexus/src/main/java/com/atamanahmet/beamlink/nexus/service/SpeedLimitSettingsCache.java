package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * In-memory file default and total speed limits. No DB read per chunk.
 */
@Component
@RequiredArgsConstructor
public class SpeedLimitSettingsCache {

    private final SettingsService settingsService;

    private volatile Double fileDefaultMbps;
    private volatile Double totalMbps;

    @PostConstruct
    void load() {
        fileDefaultMbps = settingsService.getOptionalDouble(SettingKey.TRANSFER_SPEED_CAP_MBPS)
                .orElse(null);
        totalMbps = settingsService.getOptionalDouble(SettingKey.TOTAL_SPEED_LIMIT_MBPS)
                .orElse(null);
    }

    public Double getFileDefaultMbps() {
        return fileDefaultMbps;
    }

    public Double getTotalMbps() {
        return totalMbps;
    }

    public void setFileDefaultMbps(Double mbps) {
        this.fileDefaultMbps = mbps;
    }

    public void setTotalMbps(Double mbps) {
        this.totalMbps = mbps;
    }
}
