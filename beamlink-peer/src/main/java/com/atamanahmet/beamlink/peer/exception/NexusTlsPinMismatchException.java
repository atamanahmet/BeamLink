package com.atamanahmet.beamlink.peer.exception;

import com.atamanahmet.beamlink.peer.security.NexusTlsPin;

/**
 * Stored pin does not match the probed leaf. Never auto re-pins.
 */
public class NexusTlsPinMismatchException extends RuntimeException {

    public NexusTlsPinMismatchException() {
        super(NexusTlsPin.MISMATCH);
    }
}
