package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;

/**
 * SOURCE wins when both sides pause at once.
 */
public final class PausedByResolver {

    private PausedByResolver() {}

    /**
     * Keeps SOURCE if already set or incoming, else takes incoming.
     */
    public static PauseOwner resolvePausedBy(PauseOwner current, PauseOwner incoming) {
        if (current == PauseOwner.SOURCE) {
            return PauseOwner.SOURCE;
        }
        if (incoming == PauseOwner.SOURCE) {
            return PauseOwner.SOURCE;
        }
        return incoming;
    }
}
