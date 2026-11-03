package com.atamanahmet.beamlink.peer.service;

public final class WsConnectGuard {

    private WsConnectGuard() {
    }

    /**
     * True when idle. Skip when connected, connecting, or a backoff retry is scheduled.
     */
    public static boolean shouldConnect(boolean connected, boolean connecting, boolean scheduled) {
        return !connected && !connecting && !scheduled;
    }
}
