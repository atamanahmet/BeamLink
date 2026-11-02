package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * In-memory reserve bytes. Reloads when the setting is saved.
 */
@Component
@RequiredArgsConstructor
public class DiskReserveProvider {

    private final SettingsService settingsService;

    private volatile long bytes = Long.parseLong(
            SettingKey.MIN_FREE_SPACE_BYTES.defaultValue());

    /**
     * Loads the stored value or the registry default.
     */
    @PostConstruct
    public void load() {
        refresh();
    }

    /**
     * Reads the setting once and keeps it in memory.
     */
    public void refresh() {
        String raw = settingsService.get(SettingKey.MIN_FREE_SPACE_BYTES)
                .orElse(SettingKey.MIN_FREE_SPACE_BYTES.defaultValue());
        try {
            bytes = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            bytes = Long.parseLong(SettingKey.MIN_FREE_SPACE_BYTES.defaultValue());
        }
    }

    public long getBytes() {
        return bytes;
    }
}
