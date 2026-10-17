package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.TransferProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared total and per-transfer byte buckets. Null rate means unlimited.
 */
@Service
@RequiredArgsConstructor
public class TransferSpeedLimiter {

    private final SpeedLimitSettingsCache cache;
    private final TransferProperties transferProperties;

    private final AtomicReference<RateBucket> shared = new AtomicReference<>();
    private final ConcurrentHashMap<UUID, RateBucket> perTransfer = new ConcurrentHashMap<>();

    /**
     * Blocks until payload bytes are taken from file then shared limiters.
     */
    public long acquire(UUID transferId, Double overrideMbps, int bytes)
            throws InterruptedException {
        long waitedMs = 0L;
        Double fileLimit = SpeedLimitMath.resolveFileLimit(
                overrideMbps, cache.getFileDefaultMbps());
        if (fileLimit != null) {
            waitedMs += consume(bucketFor(perTransfer, transferId, fileLimit), bytes);
        }
        Double total = cache.getTotalMbps();
        if (total != null) {
            waitedMs += consume(sharedBucket(total), bytes);
        }
        return waitedMs;
    }

    public void clearTransfer(UUID transferId) {
        perTransfer.remove(transferId);
    }

    private Bucket sharedBucket(double mbps) {
        RateBucket current = shared.get();
        if (current != null && Double.compare(current.mbps, mbps) == 0) {
            return current.bucket;
        }
        RateBucket created = new RateBucket(mbps, newBucket(mbps));
        shared.set(created);
        return created.bucket;
    }

    private Bucket bucketFor(
            ConcurrentHashMap<UUID, RateBucket> map, UUID id, double mbps) {
        RateBucket current = map.get(id);
        if (current != null && Double.compare(current.mbps, mbps) == 0) {
            return current.bucket;
        }
        RateBucket created = new RateBucket(mbps, newBucket(mbps));
        map.put(id, created);
        return created.bucket;
    }

    private Bucket newBucket(double mbps) {
        long bytesPerSec = SpeedLimitMath.toBytesPerSecond(mbps);
        long capacity = Math.max(bytesPerSec, transferProperties.getChunkSizeBytes());
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(bytesPerSec, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static long consume(Bucket bucket, int bytes) throws InterruptedException {
        long start = System.nanoTime();
        bucket.asBlocking().consume(bytes);
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static final class RateBucket {
        private final double mbps;
        private final Bucket bucket;

        private RateBucket(double mbps, Bucket bucket) {
            this.mbps = mbps;
            this.bucket = bucket;
        }
    }
}
