package com.beamlink.peer.receive

object SendCompleteRule {

    /**
     * True when status is ACTIVE and confirmed bytes equal file size.
     */
    fun shouldMarkComplete(status: String, confirmedOffset: Long, fileSize: Long): Boolean {
        return status == ReceiveSession.ACTIVE && confirmedOffset == fileSize
    }
}
