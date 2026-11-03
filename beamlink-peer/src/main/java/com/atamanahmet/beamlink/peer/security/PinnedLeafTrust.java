package com.atamanahmet.beamlink.peer.security;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;

/**
 * Pin replaces hostname checking on purpose.
 */
public final class PinnedLeafTrust extends X509ExtendedTrustManager {

    private final Supplier<String> pin;
    private final String missingMessage;
    private final String mismatchMessage;

    public PinnedLeafTrust(Supplier<String> pin, String missingMessage, String mismatchMessage) {
        this.pin = pin;
        this.missingMessage = missingMessage;
        this.mismatchMessage = mismatchMessage;
    }

    /**
     * Accepts only a leaf whose fingerprint equals the pin supplier.
     */
    public static void accept(
            X509Certificate[] chain,
            Supplier<String> pinSupplier,
            String missingMessage,
            String mismatchMessage) throws CertificateException {
        String stored = pinSupplier.get();
        if (stored == null || stored.isBlank()) {
            throw new CertificateException(missingMessage);
        }
        if (chain == null || chain.length == 0) {
            throw new CertificateException("empty certificate chain");
        }
        String seen = NexusTlsFingerprint.of(chain[0]);
        if (!stored.equals(seen)) {
            throw new CertificateException(
                    mismatchMessage + " expected=" + stored + " seen=" + seen);
        }
    }

    /**
     * Same pin check for every TrustManager overload.
     */
    private void checkPin(X509Certificate[] chain) throws CertificateException {
        accept(chain, pin, missingMessage, mismatchMessage);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkPin(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkPin(chain);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        checkPin(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        checkPin(chain);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        checkPin(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        checkPin(chain);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
