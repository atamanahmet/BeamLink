package com.atamanahmet.beamlink.peer.exception;

import lombok.Getter;

/**
 * Receiver expected a different write offset for this chunk.
 */
@Getter
public class OffsetMismatchException extends RuntimeException {

    private final long expectedOffset;

    public OffsetMismatchException(long expectedOffset) {
        super("Unexpected offset. Expected: " + expectedOffset);
        this.expectedOffset = expectedOffset;
    }
}
