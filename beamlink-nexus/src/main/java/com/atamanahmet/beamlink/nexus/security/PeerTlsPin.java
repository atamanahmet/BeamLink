package com.atamanahmet.beamlink.nexus.security;

/**
 * Messages for a peer leaf pin from stored device TLS fields.
 */
public final class PeerTlsPin {

    public static final String MISSING_PIN = "Peer TLS pin is not confirmed";
    public static final String MISMATCH = "Peer TLS pin mismatch";

    private PeerTlsPin() {
    }
}
