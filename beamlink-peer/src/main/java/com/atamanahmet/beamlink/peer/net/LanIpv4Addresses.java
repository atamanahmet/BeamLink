package com.atamanahmet.beamlink.peer.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Picks private LAN IPv4 addresses from live network interfaces.
 */
public final class LanIpv4Addresses {

    private LanIpv4Addresses() {
    }

    /**
     * Live interfaces. Empty when none match.
     */
    public static List<String> list() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return List.of();
            }
            List<Face> faces = new ArrayList<>();
            while (interfaces.hasMoreElements()) {
                faces.add(Face.from(interfaces.nextElement()));
            }
            return pick(faces);
        } catch (SocketException e) {
            return List.of();
        }
    }

    /**
     * Same pick rules for a given face list.
     */
    public static List<String> pick(List<Face> faces) {
        if (faces == null || faces.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Face face : faces) {
            if (!face.up() || face.loopback() || face.virtual()) {
                continue;
            }
            if (skipName(face.name())) {
                continue;
            }
            for (String ip : face.ipv4()) {
                if (isPrivateLan(ip) && !out.contains(ip)) {
                    out.add(ip);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * True for RFC1918 private IPv4 text.
     */
    static boolean isPrivateLan(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (ip.startsWith("10.")) {
            return true;
        }
        if (ip.startsWith("192.168.")) {
            return true;
        }
        return ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    /**
     * True when the interface name looks like docker, vpn, or vm.
     */
    static boolean skipName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("docker")
                || n.contains("veth")
                || n.startsWith("br-")
                || n.contains("vpn")
                || n.contains("tun")
                || n.contains("tap")
                || n.contains("wg")
                || n.contains("zerotier")
                || n.contains("tailscale")
                || n.contains("hamachi")
                || n.contains("virtual")
                || n.contains("vmware")
                || n.contains("vbox")
                || n.contains("hyper-v")
                || n.contains("loopback")
                || n.contains("pseudo")
                || n.contains("teredo")
                || n.contains("isatap");
    }

    /**
     * One network interface for pure tests.
     */
    public record Face(
            String name,
            boolean up,
            boolean loopback,
            boolean virtual,
            List<String> ipv4) {

        /**
         * Builds a face from a live NetworkInterface.
         */
        static Face from(NetworkInterface iface) throws SocketException {
            List<String> ipv4 = new ArrayList<>();
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    String host = address.getHostAddress();
                    if (host != null && !host.isBlank()) {
                        ipv4.add(host);
                    }
                }
            }
            return new Face(
                    iface.getDisplayName(),
                    iface.isUp(),
                    iface.isLoopback(),
                    iface.isVirtual(),
                    List.copyOf(ipv4));
        }
    }
}
