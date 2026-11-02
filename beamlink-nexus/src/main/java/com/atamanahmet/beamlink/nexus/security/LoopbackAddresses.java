package com.atamanahmet.beamlink.nexus.security;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * Checks whether a remote address is loopback or a local interface.
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

    /**
     * True when source equals claimed, or source is loopback and claimed is a local interface.
     */
    public static boolean sourceMatchesClaimed(String claimedIp, String remoteAddr) {
        if (claimedIp == null || claimedIp.isBlank() || remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress claimed = InetAddress.getByName(claimedIp);
            InetAddress source = InetAddress.getByName(remoteAddr);
            if (claimed.equals(source)) {
                return true;
            }
            return source.isLoopbackAddress() && isLocalInterfaceAddress(claimed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True when the address belongs to this machine.
     */
    public static boolean isLocalInterfaceAddress(InetAddress addr) {
        try {
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
                return true;
            }
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
