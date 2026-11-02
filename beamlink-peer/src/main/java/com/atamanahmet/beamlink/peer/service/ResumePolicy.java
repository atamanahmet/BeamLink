package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.peer.domain.enums.ResumePolicyMode;

/**
 * Pure resume rules. Default mode is OWNER_ONLY.
 */
public final class ResumePolicy {

    public static final ResumePolicyMode DEFAULT_MODE = ResumePolicyMode.OWNER_ONLY;

    private ResumePolicy() {}

    /**
     * True when this side may resume for the given pause owner.
     */
    public static boolean canResume(
            ResumePolicyMode mode, PauseOwner side, PauseOwner pausedBy) {
        if (mode == null || side == null) {
            return false;
        }
        if (pausedBy == null) {
            return true;
        }
        if (mode == ResumePolicyMode.ANYONE) {
            return true;
        }
        return side == pausedBy;
    }
}
