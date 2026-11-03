package com.atamanahmet.beamlink.peer.security;

import java.net.InetAddress;

/**
 * Checks whether a remote address is loopback.
 */
public final class LoopbackAddresses {

    private LoopbackAddresses() {
    }

    /**
     * Returns true when the address is a local loopback host.
     */
    public static boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
