package com.atamanahmet.beamlink.nexus.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory single-use enrollment challenges. 60 s TTL, size capped.
 */
@Component
public class ChallengeStore {

    public static final int EXPIRES_IN_SECONDS = 60;
    static final int MAX_ENTRIES = 1024;
    static final int MAX_PER_DEVICE = 5;
    private static final long TTL_MS = EXPIRES_IN_SECONDS * 1000L;

    public enum ConsumeResult {
        OK,
        MISSING,
        EXPIRED,
        WRONG_DEVICE
    }

    private final Map<String, Entry> byChallenge = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final int maxEntries;
    private final int maxPerDevice;
    private final long ttlMs;

    public ChallengeStore() {
        this(MAX_ENTRIES, TTL_MS, MAX_PER_DEVICE);
    }

    ChallengeStore(int maxEntries, long ttlMs) {
        this(maxEntries, ttlMs, MAX_PER_DEVICE);
    }

    ChallengeStore(int maxEntries, long ttlMs, int maxPerDevice) {
        this.maxEntries = maxEntries;
        this.ttlMs = ttlMs;
        this.maxPerDevice = maxPerDevice;
    }

    /**
     * Issues a 32-byte base64url challenge, or empty when a cap is full.
     */
    public Optional<String> issue(String deviceId, long nowMs) {
        purge(nowMs);
        if (countForDevice(deviceId) >= maxPerDevice) {
            return Optional.empty();
        }
        if (byChallenge.size() >= maxEntries) {
            return Optional.empty();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byChallenge.put(challenge, new Entry(deviceId, nowMs + ttlMs));
        return Optional.of(challenge);
    }

    /**
     * Removes the challenge. Single use even when the result is not OK.
     */
    public ConsumeResult consume(String deviceId, String challenge, long nowMs) {
        if (challenge == null || challenge.isBlank()) {
            purge(nowMs);
            return ConsumeResult.MISSING;
        }
        Entry entry = byChallenge.remove(challenge);
        if (entry == null) {
            purge(nowMs);
            return ConsumeResult.MISSING;
        }
        if (nowMs >= entry.expiresAtMs) {
            return ConsumeResult.EXPIRED;
        }
        if (!entry.deviceId.equals(deviceId)) {
            return ConsumeResult.WRONG_DEVICE;
        }
        return ConsumeResult.OK;
    }

    int size() {
        return byChallenge.size();
    }

    /**
     * Drops expired challenges. Called from issue and consume.
     */
    void purge(long nowMs) {
        Iterator<Map.Entry<String, Entry>> it = byChallenge.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> item = it.next();
            if (nowMs >= item.getValue().expiresAtMs) {
                it.remove();
            }
        }
    }

    private int countForDevice(String deviceId) {
        int n = 0;
        for (Entry entry : byChallenge.values()) {
            if (entry.deviceId.equals(deviceId)) {
                n++;
            }
        }
        return n;
    }

    private record Entry(String deviceId, long expiresAtMs) {
    }
}
