package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.exception.ForbiddenException;

import java.util.UUID;

/**
 * Party checks for inbound transfer routes.
 */
public final class TransferPartyGuard {

    public static final String DETAIL = "Access denied";
    public static final String CODE = "FORBIDDEN";

    private TransferPartyGuard() {
    }

    /**
     * Requires the caller to be the source device.
     */
    public static void requireSource(UUID caller, UUID source) {
        if (caller == null || source == null || !caller.equals(source)) {
            throw denied();
        }
    }

    /**
     * Requires the caller to be the source or the target device.
     */
    public static void requireParty(UUID caller, UUID source, UUID target) {
        if (caller == null) {
            throw denied();
        }
        if (caller.equals(source) || (target != null && caller.equals(target))) {
            return;
        }
        throw denied();
    }

    private static ForbiddenException denied() {
        return new ForbiddenException(DETAIL, CODE);
    }
}
