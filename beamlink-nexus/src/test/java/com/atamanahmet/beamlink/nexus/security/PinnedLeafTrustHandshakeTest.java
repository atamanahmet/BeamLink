package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.http.PinnedHttpClients;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinnedLeafTrustHandshakeTest {

    private static final char[] STORE_PASS = "test".toCharArray();

    private KeyPair keys;
    private X509Certificate leaf;
    private String pin;
    private HttpsServer server;
    private ExecutorService executor;

    @BeforeEach
    void startServer() throws Exception {
        keys = TestEcCerts.ecKeyPair();
        leaf = TestEcCerts.selfSigned(
                "sanless",
                keys,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        pin = NexusTlsFingerprint.of(leaf);
        SSLContext serverTls = serverContext(keys, leaf);
        server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        server.createContext("/", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void rightPinAccepted() throws Exception {
        HttpResponse<String> res = get("https://127.0.0.1:" + port() + "/", pin);
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
        assertThatThrownBy(() -> get("https://127.0.0.1:" + port() + "/", wrong))
                .hasRootCauseInstanceOf(CertificateException.class)
                .rootCause()
                .hasMessageContaining("mismatch")
                .hasMessageContaining("expected=" + wrong)
                .hasMessageContaining("seen=" + pin);
    }

    @Test
    void rightPinAcceptedFromIpAndLocalhost() throws Exception {
        int p = port();
        HttpResponse<String> byIp = get("https://127.0.0.1:" + p + "/", pin);
        HttpResponse<String> byName = get("https://localhost:" + p + "/", pin);
        assertThat(byIp.statusCode()).isEqualTo(200);
        assertThat(byName.statusCode()).isEqualTo(200);
        assertThat(byIp.body()).isEqualTo("ok");
        assertThat(byName.body()).isEqualTo("ok");
    }

    private int port() {
        return server.getAddress().getPort();
    }

    /**
     * GET through the production pin client.
     */
    private static HttpResponse<String> get(String url, String pin) throws IOException, InterruptedException {
        HttpClient client = PinnedHttpClients.forPin(pin);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Server SSLContext for one SAN-less self-signed leaf.
     */
    private static SSLContext serverContext(KeyPair pair, X509Certificate cert) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, STORE_PASS);
        store.setKeyEntry("leaf", pair.getPrivate(), STORE_PASS, new Certificate[]{cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, STORE_PASS);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ctx;
    }
}
