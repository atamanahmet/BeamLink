package com.atamanahmet.beamlink.peer.security;

import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Trusts only a nexus leaf whose SHA-256 pin matches. Pin replaces hostname checks.
 */
@Component
@RequiredArgsConstructor
public class NexusTlsPin extends X509ExtendedTrustManager {

    public static final String MISSING_PIN = "Nexus TLS pin is not confirmed";
    public static final String MISMATCH = "Nexus TLS pin mismatch";

    private final SettingsService settingsService;

    /**
     * True when a pin is stored.
     */
    public boolean hasPin() {
        return storedPin() != null;
    }

    /**
     * Stored pin or null.
     */
    public String storedPin() {
        return settingsService.get(SettingKey.NEXUS_TLS_PIN)
                .filter(v -> !v.isBlank())
                .orElse(null);
    }

    /**
     * Persists the confirmed pin.
     */
    public void store(String pin) {
        settingsService.set(SettingKey.NEXUS_TLS_PIN, pin);
    }

    /**
     * Clears the pin. Re-pin is an explicit user action.
     */
    public void clear() {
        settingsService.clear(SettingKey.NEXUS_TLS_PIN);
    }

    /**
     * TLS handshake only. Returns the leaf fingerprint. No HTTP and no store.
     */
    public String probe(String host, int port) {
        try {
            return probeLeaf(host, port, NexusTlsPin::fingerprintOf);
        } catch (Exception e) {
            throw new IllegalStateException("Connect failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handshake-only capture of the leaf fingerprint.
     */
    static String probeLeaf(String host, int port, java.util.function.Function<X509Certificate, String> format)
            throws Exception {
        AtomicReference<X509Certificate> leaf = new AtomicReference<>();
        X509TrustManager capture = capturingManager(leaf);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{capture}, new SecureRandom());
        try (SSLSocket socket = (SSLSocket) ctx.getSocketFactory().createSocket(host, port)) {
            socket.setSoTimeout(5000);
            SSLParameters params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm(null);
            socket.setSSLParameters(params);
            socket.startHandshake();
        }
        X509Certificate cert = leaf.get();
        if (cert == null) {
            throw new IllegalStateException("Connect got no certificate");
        }
        return format.apply(cert);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("client certs are not accepted");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        throw new CertificateException("client certs are not accepted");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        throw new CertificateException("client certs are not accepted");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        PinnedLeafTrust.accept(chain, this::storedPin, MISSING_PIN, MISMATCH);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        PinnedLeafTrust.accept(chain, this::storedPin, MISSING_PIN, MISMATCH);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        PinnedLeafTrust.accept(chain, this::storedPin, MISSING_PIN, MISMATCH);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /**
     * Accepts only a leaf whose fingerprint equals the pin supplier.
     */
    static void acceptPinnedLeaf(X509Certificate[] chain, Supplier<String> pinSupplier)
            throws CertificateException {
        PinnedLeafTrust.accept(chain, pinSupplier, MISSING_PIN, MISMATCH);
    }

    static String fingerprintOf(X509Certificate leaf) {
        return NexusTlsFingerprint.of(leaf);
    }

    private static X509TrustManager capturingManager(AtomicReference<X509Certificate> leaf) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain == null || chain.length == 0) {
                    throw new CertificateException("empty certificate chain");
                }
                leaf.set(chain[0]);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
