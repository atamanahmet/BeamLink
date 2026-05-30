package com.atamanahmet.beamlink.nexus.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Resolves local network IP at startup.
 * Scores interfaces to prefer real LAN adapters over VPNs, VM adapters, hotspots.
 */
@Component
@Getter
public class NexusNetworkConfig {

    private String resolvedIp;

    @PostConstruct
    public void resolve() {
        this.resolvedIp = resolveLocalIp();
    }

    private String resolveLocalIp() {
        try {
            String bestAddress = null;
            int bestScore = -1;

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual())
                    continue;

                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") ||
                        name.contains("vbox") || name.contains("hyper-v") ||
                        name.contains("loopback") || name.contains("pseudo") ||
                        name.contains("teredo") || name.contains("isatap"))
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    if (address.isLoopbackAddress() || !(address instanceof Inet4Address))
                        continue;

                    String ip = address.getHostAddress();
                    int score = scoreAddress(ip);

                    if (score > bestScore) {
                        bestScore = score;
                        bestAddress = ip;
                    }
                }
            }

            if (bestAddress != null) return bestAddress;

        } catch (SocketException e) {
            throw new IllegalStateException("Failed to resolve local IP address", e);
        }
        throw new IllegalStateException("No suitable network interface found");
    }

    private int scoreAddress(String ip) {
        if (ip.startsWith("192.168.0.") || ip.startsWith("192.168.1."))  return 30;
        if (ip.startsWith("192.168."))                                    return 20;
        if (ip.startsWith("10."))                                         return 15;
        if (ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..*"))               return 10;
        if (ip.startsWith("169.254."))                                    return 1;
        return 5;
    }
}