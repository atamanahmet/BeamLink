package com.atamanahmet.beamlink.nexus.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory last chunk time for active receive transfers.
 */
@Component
public class ReceiveStallTracker {

    private final ConcurrentHashMap<UUID, Instant> lastChunkAtByTransfer = new ConcurrentHashMap<>();

    /**
     * Records now as the last chunk time for this transfer.
     */
    public void touch(UUID transferId) {
        lastChunkAtByTransfer.put(transferId, Instant.now());
    }

    /**
     * Drops tracking for this transfer.
     */
    public void remove(UUID transferId) {
        lastChunkAtByTransfer.remove(transferId);
    }

    /**
     * Last chunk time for one transfer, or null if not tracked.
     */
    public Instant getLastTouch(UUID transferId) {
        return lastChunkAtByTransfer.get(transferId);
    }

    /**
     * Snapshot of tracked receive transfers and last chunk times.
     */
    public Map<UUID, Instant> snapshot() {
        return Map.copyOf(lastChunkAtByTransfer);
    }
}
