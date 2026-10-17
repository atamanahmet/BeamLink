package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.ResumePolicyMode;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the Nexus resume policy setting.
 */
@Component
@RequiredArgsConstructor
public class NexusResumePolicyStore {

    private final SettingsService settingsService;

    /**
     * Returns the stored mode, or OWNER_ONLY when missing or invalid.
     */
    public ResumePolicyMode getMode() {
        String raw = settingsService.get(
                SettingKey.TRANSFER_RESUME_POLICY,
                ResumePolicy.DEFAULT_MODE.name());
        try {
            return ResumePolicyMode.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException e) {
            return ResumePolicy.DEFAULT_MODE;
        }
    }

    /**
     * Persists a valid mode.
     */
    public void setMode(ResumePolicyMode mode) {
        settingsService.set(SettingKey.TRANSFER_RESUME_POLICY, mode.name());
    }
}
