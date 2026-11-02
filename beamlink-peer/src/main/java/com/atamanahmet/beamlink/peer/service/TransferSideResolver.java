package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves whether this node is SOURCE or TARGET for a transfer.
 */
@Component
@RequiredArgsConstructor
public class TransferSideResolver {

    private final DeviceService deviceService;

    /**
     * Uses local device id, then target fields as fallback.
     */
    public PauseOwner resolve(UUID sourceDeviceId, UUID targetDeviceId, String targetIp) {
        UUID localId = deviceService.getDeviceId();
        if (localId != null && localId.equals(sourceDeviceId)) {
            return PauseOwner.SOURCE;
        }
        if (localId != null && localId.equals(targetDeviceId)) {
            return PauseOwner.TARGET;
        }
        if (targetDeviceId == null && sourceDeviceId != null) {
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
