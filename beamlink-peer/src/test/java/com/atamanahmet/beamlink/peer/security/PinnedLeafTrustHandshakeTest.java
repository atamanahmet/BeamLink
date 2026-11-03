package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.http.PinnedHttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedLeafTrustHandshakeTest {

    private TestSanlessHttps https;

    @BeforeEach
    void startServer() throws Exception {
        https = TestSanlessHttps.start();
    }

    @AfterEach
    void stopServer() {
        if (https != null) {
            https.stop();
        }
    }

    @Test
    void rightPinAccepted() throws Exception {
        HttpResponse<String> res = get("https://127.0.0.1:" + https.port() + "/", https.pin);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("ok");
    }

    @Test
    void wrongPinRejected() throws Exception {
        X509Certificate other = TestEcCerts.selfSigned(
                "other",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        String wrong = NexusTlsFingerprint.of(other);
        assertThatThrownBy(() -> get("https://127.0.0.1:" + https.port() + "/", wrong))
                .hasRootCauseInstanceOf(CertificateException.class)
                .rootCause()
                .hasMessageContaining("mismatch")
                .hasMessageContaining("expected=" + wrong)
                .hasMessageContaining("seen=" + https.pin);
    }

    @Test
    void rightPinAcceptedFromIpAndLocalhost() throws Exception {
        int p = https.port();
        HttpResponse<String> byIp = get("https://127.0.0.1:" + p + "/", https.pin);
        HttpResponse<String> byName = get("https://localhost:" + p + "/", https.pin);
        assertThat(byIp.statusCode()).isEqualTo(200);
        assertThat(byName.statusCode()).isEqualTo(200);
        assertThat(byIp.body()).isEqualTo("ok");
        assertThat(byName.body()).isEqualTo("ok");
    }

    /**
     * GET through the production pin client.
     */
    private static HttpResponse<String> get(String url, String pin) throws IOException, InterruptedException {
        HttpClient client = PinnedHttpClients.forPin(pin);
        return TestSanlessHttps.get(client, url);
    }
}
