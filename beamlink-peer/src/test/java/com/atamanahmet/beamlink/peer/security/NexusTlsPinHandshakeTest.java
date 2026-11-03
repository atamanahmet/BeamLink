package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.service.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusTlsPinHandshakeTest {

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
    void rightPinAcceptedWithoutDisable() throws Exception {
        HttpResponse<String> res = get("https://127.0.0.1:" + https.port() + "/", pinned(https.pin));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("ok");
    }

    @Test
    void rightPinAcceptedFromIpAndLocalhostWithoutDisable() throws Exception {
        NexusTlsPin pin = pinned(https.pin);
        int p = https.port();
        HttpResponse<String> byIp = get("https://127.0.0.1:" + p + "/", pin);
        HttpResponse<String> byName = get("https://localhost:" + p + "/", pin);
        assertThat(byIp.statusCode()).isEqualTo(200);
        assertThat(byName.statusCode()).isEqualTo(200);
        assertThat(byIp.body()).isEqualTo("ok");
        assertThat(byName.body()).isEqualTo("ok");
    }

    @Test
    void wrongPinRejectedWithoutDisable() throws Exception {
        X509Certificate other = TestEcCerts.selfSigned(
                "other",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        String wrong = NexusTlsFingerprint.of(other);
        assertThatThrownBy(() -> get("https://127.0.0.1:" + https.port() + "/", pinned(wrong)))
                .hasRootCauseInstanceOf(CertificateException.class)
                .rootCause()
                .hasMessageContaining(NexusTlsPin.MISMATCH)
                .hasMessageContaining("expected=" + wrong)
                .hasMessageContaining("seen=" + https.pin);
    }

    /**
     * GET through NexusTlsPin with default HTTPS endpoint identification.
     */
    private static HttpResponse<String> get(String url, NexusTlsPin pin) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{pin}, new SecureRandom());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(ctx)
                .build();
        return TestSanlessHttps.get(client, url);
    }

    private static NexusTlsPin pinned(String pin) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.NEXUS_TLS_PIN)).thenReturn(Optional.of(pin));
        return new NexusTlsPin(settings);
    }
}
