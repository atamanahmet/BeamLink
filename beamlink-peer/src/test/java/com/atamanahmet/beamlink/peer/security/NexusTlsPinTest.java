package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.service.SettingsService;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusTlsPinTest {

    @Test
    void acceptsMatchingLeaf() throws Exception {
        X509Certificate leaf = valid("match");
        NexusTlsPin pin = pinned(leaf);
        pin.checkServerTrusted(new X509Certificate[]{leaf}, "ECDHE_ECDSA");
    }

    @Test
    void rejectsDifferentValidLeaf() throws Exception {
        X509Certificate expected = valid("a");
        X509Certificate other = valid("b");
        NexusTlsPin pin = pinned(expected);
        assertThatThrownBy(() -> pin.checkServerTrusted(new X509Certificate[]{other}, "ECDHE_ECDSA"))
                .isInstanceOf(CertificateException.class)
                .hasMessageContaining(NexusTlsPin.MISMATCH)
                .hasMessageContaining(NexusTlsFingerprint.of(expected))
                .hasMessageContaining(NexusTlsFingerprint.of(other));
    }

    @Test
    void rejectsDifferentExpiredLeaf() throws Exception {
        X509Certificate expected = valid("a");
        X509Certificate expired = TestEcCerts.selfSigned(
                "expired",
                Instant.now().minus(400, ChronoUnit.DAYS),
                Instant.now().minus(10, ChronoUnit.DAYS));
        NexusTlsPin pin = pinned(expected);
        assertThatThrownBy(() -> pin.checkServerTrusted(new X509Certificate[]{expired}, "ECDHE_ECDSA"))
                .isInstanceOf(CertificateException.class)
                .hasMessageContaining(NexusTlsPin.MISMATCH)
                .hasMessageContaining(NexusTlsFingerprint.of(expected))
                .hasMessageContaining(NexusTlsFingerprint.of(expired));
    }

    @Test
    void acceptsMatchingExpiredLeaf() throws Exception {
        X509Certificate expired = TestEcCerts.selfSigned(
                "same",
                Instant.now().minus(400, ChronoUnit.DAYS),
                Instant.now().minus(10, ChronoUnit.DAYS));
        NexusTlsPin pin = pinned(expired);
        pin.checkServerTrusted(new X509Certificate[]{expired}, "ECDHE_ECDSA");
    }

    @Test
    void rejectsWhenPinMissing() throws Exception {
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.NEXUS_TLS_PIN)).thenReturn(Optional.empty());
        NexusTlsPin pin = new NexusTlsPin(settings);
        X509Certificate leaf = valid("any");
        assertThatThrownBy(() -> pin.checkServerTrusted(new X509Certificate[]{leaf}, "ECDHE_ECDSA"))
                .isInstanceOf(CertificateException.class)
                .hasMessage(NexusTlsPin.MISSING_PIN);
    }

    private static NexusTlsPin pinned(X509Certificate leaf) {
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.NEXUS_TLS_PIN)).thenReturn(Optional.of(NexusTlsFingerprint.of(leaf)));
        return new NexusTlsPin(settings);
    }

    private static X509Certificate valid(String cn) throws Exception {
        return TestEcCerts.selfSigned(
                cn,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
    }
}
