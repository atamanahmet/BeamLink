package com.atamanahmet.beamlink.peer.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanIpv4AddressesTest {

    @Test
    void picksPrivateLanAndSkipsDockerVpnAndPublic() {
        List<String> ips = LanIpv4Addresses.pick(List.of(
                new LanIpv4Addresses.Face(
                        "Ethernet", true, false, false, List.of("192.168.1.10", "8.8.8.8")),
                new LanIpv4Addresses.Face(
                        "docker0", true, false, false, List.of("172.17.0.1")),
                new LanIpv4Addresses.Face(
                        "Tailscale Tunnel", true, false, false, List.of("100.64.1.2")),
                new LanIpv4Addresses.Face(
                        "Wi-Fi", true, false, false, List.of("10.0.0.5")),
                new LanIpv4Addresses.Face(
                        "Down NIC", false, false, false, List.of("192.168.0.2")),
                new LanIpv4Addresses.Face(
                        "Loopback", true, true, false, List.of("127.0.0.1")),
                new LanIpv4Addresses.Face(
                        "Virtual Box", true, false, true, List.of("192.168.56.1"))));

        assertThat(ips).containsExactly("192.168.1.10", "10.0.0.5");
    }

    @Test
    void noneFoundWhenNoMatch() {
        List<String> ips = LanIpv4Addresses.pick(List.of(
                new LanIpv4Addresses.Face(
                        "docker0", true, false, false, List.of("172.17.0.1")),
                new LanIpv4Addresses.Face(
                        "Public", true, false, false, List.of("1.2.3.4"))));

        assertThat(ips).isEmpty();
    }
}
