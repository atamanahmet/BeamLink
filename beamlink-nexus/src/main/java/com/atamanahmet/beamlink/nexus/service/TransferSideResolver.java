package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves whether this node is SOURCE or TARGET for a transfer.
 */
@Component
@RequiredArgsConstructor
public class TransferSideResolver {

    private final NexusService nexusService;

    /**
     * Uses local nexus id, then target fields as fallback.
     */
    public PauseOwner resolve(UUID sourceAgentId, UUID targetAgentId, String targetIp) {
        UUID localId = nexusService.getNexusId();
        if (localId != null && localId.equals(sourceAgentId)) {
            return PauseOwner.SOURCE;
        }
        if (localId != null && localId.equals(targetAgentId)) {
            return PauseOwner.TARGET;
        }
        if (targetAgentId == null && sourceAgentId != null) {
            return PauseOwner.TARGET;
        }
        if (targetIp != null && !targetIp.isBlank()) {
            return PauseOwner.SOURCE;
        }
        return PauseOwner.TARGET;
    }

    /**
     * Offline peer is the other side of this node.
     */
    public static PauseOwner opposite(PauseOwner localSide) {
        return localSide == PauseOwner.SOURCE ? PauseOwner.TARGET : PauseOwner.SOURCE;
    }
}
