package com.atamanahmet.beamlink.peer.service;

/**
 * Picks FAILED vs rollback when the target rejects register.
 */
public final class RegisterRejectRule {

    private RegisterRejectRule() {}

    /**
     * True when the local job should stay FAILED with the peer message.
     */
    public static boolean keepFailed(String code) {
        return "RECEIVE_IN_PROGRESS".equals(code);
    }

    /**
     * Uses the JSON detail when present, else the fallback text.
     */
    public static String visibleMessage(String jsonMessage, String fallback) {
        if (jsonMessage == null || jsonMessage.isBlank()) {
            return fallback;
        }
        return jsonMessage;
    }
}
