package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.ForbiddenException;

import java.util.UUID;

/**
 * Party checks for inbound transfer routes. Device principals only.
 */
public final class TransferPartyGuard {

    public static final String DETAIL = "Access denied";
    public static final String CODE = "FORBIDDEN";

    private TransferPartyGuard() {
    }

    /**
     * Requires a device principal equal to the source id.
     */
    public static void requireSource(Object principal, UUID sourceDeviceId) {
        UUID deviceId = deviceId(principal);
        if (deviceId == null || sourceDeviceId == null || !deviceId.equals(sourceDeviceId)) {
            throw denied();
        }
    }

    /**
     * Requires a device principal equal to the source or the target id.
     */
    public static void requireParty(Object principal, UUID sourceDeviceId, UUID targetDeviceId) {
        UUID deviceId = deviceId(principal);
        if (deviceId == null) {
            throw denied();
        }
        if (deviceId.equals(sourceDeviceId) || deviceId.equals(targetDeviceId)) {
            return;
        }
        throw denied();
    }

    private static UUID deviceId(Object principal) {
        if (principal instanceof UUID id) {
            return id;
        }
        return null;
    }

    private static ForbiddenException denied() {
        return new ForbiddenException(DETAIL, CODE);
    }
}
