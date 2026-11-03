package com.atamanahmet.beamlink.nexus.service;

/**
 * Maps a lost receiver session to FAILED. Never pause, never restart at 0.
 */
public final class SessionLostRule {

    public static final String CODE = "SESSION_NOT_FOUND";
    public static final String MESSAGE = "Receiver lost the transfer, send again";

    private SessionLostRule() {}

    /**
     * True when the peer says the receive session is gone.
     */
    public static boolean isLost(String code) {
        return CODE.equals(code);
    }
}
