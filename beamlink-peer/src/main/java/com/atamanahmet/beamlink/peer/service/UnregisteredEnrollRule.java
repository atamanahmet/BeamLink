package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;

/**
 * When an unregistered device may start enrollment.
 */
public final class UnregisteredEnrollRule {

    private UnregisteredEnrollRule() {
    }

    /**
     * True when URL and pin are ready, state is UNREGISTERED, and no enroll is running.
     */
    public static boolean shouldEnroll(
            boolean urlReady,
            boolean pinReady,
            DeviceState state,
            boolean inProgress) {
        return urlReady
                && pinReady
                && state == DeviceState.UNREGISTERED
                && !inProgress;
    }
}
