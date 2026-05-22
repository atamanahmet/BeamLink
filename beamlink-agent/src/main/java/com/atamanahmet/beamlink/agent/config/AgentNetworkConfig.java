package com.atamanahmet.beamlink.agent.config;

import jakarta.annotation.PostConstruct;

import lombok.Getter;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

import java.util.Enumeration;

/**
 * Resolves the local network IP address at startup by querying the OS directly.
 * If multiple interfaces are present, the first valid non-loopback
 * IPv4 address is used. Override via settings UI takes precedence.
 */
@Component
@Getter
public class AgentNetworkConfig {

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

                // Skip known virtual/hotspot adapter name prefixes
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

    /**
     * Higher score = more likely to be the real LAN interface.
     * Prefers typical home/office subnets over link-local or
     * auto-assigned ranges like 192.168.208.x (Windows hotspot).
     */
    private int scoreAddress(String ip) {
        // 192.168.0.x / 192.168.1.x — most common home routers
        if (ip.startsWith("192.168.0.") || ip.startsWith("192.168.1."))  return 30;
        // Other 192.168.x.x ranges
        if (ip.startsWith("192.168."))                                    return 20;
        // 10.x.x.x corporate networks
        if (ip.startsWith("10."))                                         return 15;
        // 172.16–31.x.x private ranges
        if (ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..*"))               return 10;
        // 169.254.x.x link-local (APIPA) — last resort
        if (ip.startsWith("169.254."))                                    return 1;
        return 5;
    }
}