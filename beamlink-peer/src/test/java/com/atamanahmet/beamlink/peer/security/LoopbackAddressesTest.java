package com.atamanahmet.beamlink.peer.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackAddressesTest {

    @Test
    void acceptsLoopbackHosts() {
        assertTrue(LoopbackAddresses.isLoopback("127.0.0.1"));
        assertTrue(LoopbackAddresses.isLoopback("::1"));
        assertTrue(LoopbackAddresses.isLoopback("localhost"));
    }

    @Test
    void rejectsBlankAndRemote() {
        assertFalse(LoopbackAddresses.isLoopback(null));
        assertFalse(LoopbackAddresses.isLoopback(""));
        assertFalse(LoopbackAddresses.isLoopback("8.8.8.8"));
    }
}
