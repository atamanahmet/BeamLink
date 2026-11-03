package com.atamanahmet.beamlink.peer.service;

/**
 * When mDNS active scan should stop.
 */
public final class MdnsDiscoveryRule {

    private MdnsDiscoveryRule() {
    }

    /**
     * True when a suggestion exists or the URL is already stored.
     */
    public static boolean stopScan(boolean suggestionMade, boolean urlStored) {
        return suggestionMade || urlStored;
    }
}
