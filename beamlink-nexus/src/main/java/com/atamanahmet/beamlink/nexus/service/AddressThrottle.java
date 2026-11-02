package com.atamanahmet.beamlink.nexus.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Counts failures per remote address in a rolling window.
 */
public class AddressThrottle {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_ADDRESSES = 1024;

    private final Map<String, Deque<Long>> failuresByAddress = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> addressOrder = new ConcurrentLinkedQueue<>();
    private final long windowMs;
    private final int maxFailures;
    private final int maxAddresses;

    public AddressThrottle() {
        this(WINDOW_MS, MAX_FAILURES, MAX_ADDRESSES);
    }

    AddressThrottle(long windowMs, int maxFailures) {
        this(windowMs, maxFailures, MAX_ADDRESSES);
    }

    AddressThrottle(long windowMs, int maxFailures, int maxAddresses) {
        this.windowMs = windowMs;
        this.maxFailures = maxFailures;
        this.maxAddresses = maxAddresses;
    }

    /**
     * Whole seconds until retry, rounded up, minimum 1.
     */
    static int retryAfterSeconds(long remainingMs) {
        if (remainingMs <= 0L) {
            return 1;
        }
        long seconds = (remainingMs + 999L) / 1000L;
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    /**
     * Seconds until the oldest failure in the window expires.
     */
    public int retryAfterSeconds(String remoteAddress, long nowMs) {
        Deque<Long> times = failuresByAddress.get(remoteAddress);
        if (times == null) {
            return retryAfterSeconds(0L);
        }
        long remainingMs;
        synchronized (times) {
            prune(times, nowMs);
            Long oldest = times.peekFirst();
            remainingMs = oldest == null ? 0L : oldest + windowMs - nowMs;
        }
        return retryAfterSeconds(remainingMs);
    }

    /**
     * Returns true when this address is over the failure limit.
     */
    public boolean isThrottled(String remoteAddress, long nowMs) {
        purgeExpired(nowMs);
        Deque<Long> times = failuresByAddress.get(remoteAddress);
        if (times == null) {
            return false;
        }
        synchronized (times) {
            prune(times, nowMs);
            return times.size() >= maxFailures;
        }
    }

    /**
     * Records a failed attempt for the address.
     */
    public void recordFailure(String remoteAddress, long nowMs) {
        purgeExpired(nowMs);
        boolean created = failuresByAddress.putIfAbsent(remoteAddress, new ArrayDeque<>()) == null;
        if (created) {
            addressOrder.add(remoteAddress);
        }
        Deque<Long> times = failuresByAddress.get(remoteAddress);
        synchronized (times) {
            prune(times, nowMs);
            times.addLast(nowMs);
        }
        enforceBound();
    }

    /**
     * Clears failures after a success.
     */
    public void clear(String remoteAddress) {
        failuresByAddress.remove(remoteAddress);
    }

    /**
     * Drops every tracked address.
     */
    public void clearAll() {
        failuresByAddress.clear();
        addressOrder.clear();
    }

    /**
     * Current tracked address count after purge. For tests.
     */
    int addressCount() {
        return failuresByAddress.size();
    }

    private void purgeExpired(long nowMs) {
        Iterator<Map.Entry<String, Deque<Long>>> it = failuresByAddress.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> times = entry.getValue();
            synchronized (times) {
                prune(times, nowMs);
                if (times.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private void enforceBound() {
        while (failuresByAddress.size() > maxAddresses) {
            String oldest = addressOrder.poll();
            if (oldest == null) {
                break;
            }
            failuresByAddress.remove(oldest);
        }
    }

    private void prune(Deque<Long> times, long nowMs) {
        long floor = nowMs - windowMs;
        while (!times.isEmpty() && times.peekFirst() < floor) {
            times.removeFirst();
        }
    }
}
