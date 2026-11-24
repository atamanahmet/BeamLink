package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.exception.TransferErrorCodes;

/**
 * Picks FAILED vs rollback when the target rejects register.
 */
public final class RegisterRejectRule {

    private RegisterRejectRule() {}

    /**
     * At register no bytes moved, so FAILED with the peer message. Chunk phase pauses instead.
     */
    public static boolean keepFailed(String code) {
        return TransferErrorCodes.RECEIVE_IN_PROGRESS.equals(code);
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
