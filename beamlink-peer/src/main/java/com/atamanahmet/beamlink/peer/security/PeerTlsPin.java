package com.atamanahmet.beamlink.peer.security;

/**
 * Messages for a peer leaf pin from the nexus peer list.
 */
public final class PeerTlsPin {

    public static final String MISSING_PIN = "Peer TLS pin is not confirmed";
    public static final String MISMATCH = "Peer TLS pin mismatch";

    private PeerTlsPin() {
    }
}
