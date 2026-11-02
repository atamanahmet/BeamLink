package com.atamanahmet.beamlink.nexus.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFailureThrottleTest {

    @Test
    void throttleCounterAsPureLogic() {
        LoginFailureThrottle throttle = new LoginFailureThrottle(60_000L, 5);
        String addr = "127.0.0.1";
        long now = 1_000_000L;

        assertFalse(throttle.isThrottled(addr, now));
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(addr, now + i);
        }
        assertTrue(throttle.isThrottled(addr, now + 10));

        assertFalse(throttle.isThrottled(addr, now + 60_001L));
        throttle.clear(addr);
        assertFalse(throttle.isThrottled(addr, now + 10));
    }

    @Test
    void expiredEntriesEvictedOnTouch() {
        LoginFailureThrottle throttle = new LoginFailureThrottle(60_000L, 5, 1024);
        long now = 1_000_000L;
        throttle.recordFailure("a", now);
        throttle.recordFailure("b", now);
        assertEquals(2, throttle.addressCount());

        assertFalse(throttle.isThrottled("a", now + 60_001L));
        assertEquals(0, throttle.addressCount());
    }

    @Test
    void retryAfterSecondsRoundsUpAtLeastOne() {
        assertEquals(1, AddressThrottle.retryAfterSeconds(0L));
        assertEquals(1, AddressThrottle.retryAfterSeconds(-1L));
        assertEquals(1, AddressThrottle.retryAfterSeconds(1L));
        assertEquals(1, AddressThrottle.retryAfterSeconds(1000L));
        assertEquals(2, AddressThrottle.retryAfterSeconds(1001L));
        assertEquals(60, AddressThrottle.retryAfterSeconds(60_000L));
    }

    @Test
    void mapNeverGrowsPastBound() {
        int bound = 3;
        LoginFailureThrottle throttle = new LoginFailureThrottle(60_000L, 5, bound);
        long now = 1_000_000L;
        for (int i = 0; i < 10; i++) {
            throttle.recordFailure("addr-" + i, now + i);
            assertTrue(throttle.addressCount() <= bound);
        }
        assertEquals(bound, throttle.addressCount());
    }
}
