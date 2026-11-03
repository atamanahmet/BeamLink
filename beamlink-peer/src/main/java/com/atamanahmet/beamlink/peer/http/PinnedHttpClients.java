package com.atamanahmet.beamlink.peer.http;

import com.atamanahmet.beamlink.peer.security.PeerTlsPin;
import com.atamanahmet.beamlink.peer.security.PinnedLeafTrust;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * JDK HttpClient that trusts one listed peer pin and skips hostname checks.
 */
public final class PinnedHttpClients {

    private PinnedHttpClients() {
    }

    /**
     * Client for one peer fingerprint from the nexus list.
     */
    public static HttpClient forPin(String pin) {
        try {
            PinnedLeafTrust trust = new PinnedLeafTrust(
                    () -> pin, PeerTlsPin.MISSING_PIN, PeerTlsPin.MISMATCH);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{trust}, new SecureRandom());
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .sslContext(ctx)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Peer TLS client failed", e);
        }
    }
}
