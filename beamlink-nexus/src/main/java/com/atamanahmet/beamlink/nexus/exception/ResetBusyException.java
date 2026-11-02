package com.atamanahmet.beamlink.nexus.exception;

/**
 * Thrown when reset is refused because a transfer is still running.
 */
public class ResetBusyException extends RuntimeException {

    private final int count;

    public ResetBusyException(String message, int count) {
        super(message);
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
