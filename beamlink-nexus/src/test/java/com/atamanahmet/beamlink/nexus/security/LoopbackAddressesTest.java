package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

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

    @Test
    void sourceEqualsClaimed() {
        assertTrue(LoopbackAddresses.sourceMatchesClaimed("10.0.0.8", "10.0.0.8"));
    }

    @Test
    void sourceOtherIpRejected() {
        assertFalse(LoopbackAddresses.sourceMatchesClaimed("10.0.0.8", "10.0.0.9"));
    }

    @Test
    void loopbackPlusForeignIpRejected() {
        assertFalse(LoopbackAddresses.sourceMatchesClaimed("8.8.8.8", "127.0.0.1"));
    }

    @Test
    void loopbackPlusLocalInterfaceAccepted() throws Exception {
        String local = firstNonLoopbackHost();
        assertTrue(local != null && !local.isBlank());
        assertTrue(LoopbackAddresses.sourceMatchesClaimed(local, "127.0.0.1"));
    }

    private static String firstNonLoopbackHost() throws Exception {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (!nic.isUp()) {
                continue;
            }
            Enumeration<InetAddress> addrs = nic.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        return InetAddress.getLocalHost().getHostAddress();
    }
}
