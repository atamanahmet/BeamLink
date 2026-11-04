package com.beamlink.peer.send

import java.util.UUID

/**
 * Pure send-resume decisions. No IO.
 */
object SendResumeRule {

    /**
     * True when the receiver already has this transfer, so skip POST /receive.
     */
    fun skipRegister(existingTransferId: UUID?): Boolean {
        return existingTransferId != null
    }
}
