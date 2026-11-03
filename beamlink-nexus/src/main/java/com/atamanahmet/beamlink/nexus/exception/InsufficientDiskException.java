package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.service.DiskReserveRule;

/**
 * Receiver disk is below the reserve. Maps to 507 INSUFFICIENT_DISK.
 */
public class InsufficientDiskException extends RuntimeException {

    public InsufficientDiskException() {
        super(DiskReserveRule.DETAIL);
    }
}
