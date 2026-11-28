package com.beamlink.peer.service

import com.beamlink.peer.exception.ForbiddenException
import com.beamlink.peer.security.PeerJwtSubject
import java.util.UUID

/**
 * Party checks for inbound transfer routes.
 */
object TransferPartyGuard {

    const val DETAIL = "Access denied"
    const val CODE = "FORBIDDEN"

    /**
     * Requires the caller to be the source device.
     */
    fun requireSource(caller: UUID?, source: String?) {
        val sourceId = PeerJwtSubject.parseUuidOrNull(source)
        if (caller == null || sourceId == null || caller != sourceId) {
            throw ForbiddenException(DETAIL, CODE)
        }
    }

    /**
     * Requires the caller to be the source or the target device.
     */
    fun requireParty(caller: UUID?, source: String?, target: String?) {
        if (caller == null) {
            throw ForbiddenException(DETAIL, CODE)
        }
        val sourceId = PeerJwtSubject.parseUuidOrNull(source)
        if (sourceId != null && caller == sourceId) {
            return
        }
        val targetId = PeerJwtSubject.parseUuidOrNull(target)
        if (targetId != null && caller == targetId) {
            return
        }
        throw ForbiddenException(DETAIL, CODE)
    }
}
