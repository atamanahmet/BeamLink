package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeerTransportTest {

    @Test
    void listedGivesHttpsAndPinNeverHttp() {
        PeerTransport t = PeerTransport.of("aaaa bbbb", 9091, 9090);
        assertThat(t.scheme()).isEqualTo("https");
        assertThat(t.port()).isEqualTo(9091);
        assertThat(t.pin()).isEqualTo("aaaa bbbb");
        assertThat(t.baseUrl("10.0.0.2")).isEqualTo("https://10.0.0.2:9091");
        assertThat(t.scheme()).isNotEqualTo("http");
    }

    @Test
    void noneRefusesWithoutFingerprint() {
        assertThatThrownBy(() -> PeerTransport.of(null, null, 9090))
                .isInstanceOf(PeerTransport.MissingPinException.class)
                .hasMessage("Peer TLS pin missing");
    }

    @Test
    void listedMissingPortThrows() {
        assertThatThrownBy(() -> PeerTransport.of("aaaa bbbb", null, 9090))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Peer TLS pin listed but tlsPort missing");
    }
}
