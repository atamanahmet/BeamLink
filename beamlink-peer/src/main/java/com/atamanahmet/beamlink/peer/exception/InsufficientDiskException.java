package com.atamanahmet.beamlink.peer.exception;

import com.atamanahmet.beamlink.peer.service.DiskReserveRule;

/**
 * Receiver disk is below the reserve. Maps to 507 INSUFFICIENT_DISK.
 */
public class InsufficientDiskException extends RuntimeException {

    public InsufficientDiskException() {
        super(DiskReserveRule.DETAIL);
    }
}
