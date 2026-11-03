package com.atamanahmet.beamlink.peer.security;

/**
 * Peer URL choice from the nexus peer list. Listed pin is https. Missing pin is refused.
 */
public final class PeerTransport {

    public static final class MissingPinException extends IllegalStateException {
        public MissingPinException() {
            super("Peer TLS pin missing");
        }
    }

    private final String scheme;
    private final int port;
    private final String pin;

    private PeerTransport(String scheme, int port, String pin) {
        this.scheme = scheme;
        this.port = port;
        this.pin = pin;
    }

    /**
     * https plus pin when tlsFingerprint is listed. Missing pin throws MissingPinException.
     */
    public static PeerTransport of(String tlsFingerprint, Integer tlsPort, int httpPort) {
        if (tlsFingerprint != null && !tlsFingerprint.isBlank()) {
            if (tlsPort == null || tlsPort <= 0) {
                throw new IllegalStateException("Peer TLS pin listed but tlsPort missing");
            }
            return new PeerTransport("https", tlsPort, tlsFingerprint);
        }
        throw new MissingPinException();
    }

    public String scheme() {
        return scheme;
    }

    public int port() {
        return port;
    }

    /**
     * Listed fingerprint. Never null after of() succeeds.
     */
    public String pin() {
        return pin;
    }

    /**
     * scheme://ip:port with no path.
     */
    public String baseUrl(String ip) {
        return scheme + "://" + ip + ":" + port;
    }
}
