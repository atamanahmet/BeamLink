package com.beamlink.peer.receive

/**
 * Pure check that a write leaves the reserve free.
 */
object DiskReserveRule {

    /**
     * True when usable space covers bytesNeeded plus reserve.
     */
    fun allowed(usable: Long, bytesNeeded: Long, reserve: Long): Boolean {
        if (usable < 0L || bytesNeeded < 0L || reserve < 0L) {
            return false
        }
        if (bytesNeeded > Long.MAX_VALUE - reserve) {
            return false
        }
        return usable >= bytesNeeded + reserve
    }
}
