package com.atamanahmet.beamlink.peer.security;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

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
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local HTTPS server with a SAN-less self-signed leaf.
 */
final class TestSanlessHttps {

    private static final char[] STORE_PASS = "test".toCharArray();

    final KeyPair keys;
    final X509Certificate leaf;
    final String pin;
    private final HttpsServer server;
    private final ExecutorService executor;

    private TestSanlessHttps(
            KeyPair keys,
            X509Certificate leaf,
            String pin,
            HttpsServer server,
            ExecutorService executor) {
        this.keys = keys;
        this.leaf = leaf;
        this.pin = pin;
        this.server = server;
        this.executor = executor;
    }

    /**
     * Starts a one-shot SAN-less HTTPS server on an ephemeral port.
     */
    static TestSanlessHttps start() throws Exception {
        KeyPair keys = TestEcCerts.ecKeyPair();
        X509Certificate leaf = TestEcCerts.selfSigned(
                "sanless",
                keys,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        String pin = NexusTlsFingerprint.of(leaf);
        SSLContext serverTls = serverContext(keys, leaf);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        server.createContext("/", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
        return new TestSanlessHttps(keys, leaf, pin, server, executor);
    }

    int port() {
        return server.getAddress().getPort();
    }

    /**
     * GET with a 5s timeout.
     */
    static HttpResponse<String> get(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    void stop() {
        server.stop(0);
        executor.shutdownNow();
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
