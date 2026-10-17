package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.enums.ResumePolicyMode;
import org.springframework.stereotype.Component;

/**
 * In-memory resume policy synced from Nexus ping.
 */
@Component
public class AgentResumePolicyHolder {

    private volatile ResumePolicyMode mode = ResumePolicy.DEFAULT_MODE;

    /**
     * Returns the current mode, default OWNER_ONLY.
     */
    public ResumePolicyMode getMode() {
        return mode;
    }

    /**
     * Updates the mode from a Nexus ping value.
     */
    public void updateFromPing(String resumePolicy) {
        if (resumePolicy == null || resumePolicy.isBlank()) {
            return;
        }
        try {
            mode = ResumePolicyMode.valueOf(resumePolicy.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // keep previous mode
        }
    }
}
