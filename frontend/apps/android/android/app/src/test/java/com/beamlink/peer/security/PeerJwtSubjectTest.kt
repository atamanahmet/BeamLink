package com.beamlink.peer.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Pure JWT subject UUID parse cases.
 */
class PeerJwtSubjectTest {

    @Test
    fun validUuid() {
        val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
        assertEquals(id, PeerJwtSubject.parseUuidOrNull(id.toString()))
    }

    @Test
    fun nullIsRejected() {
        assertNull(PeerJwtSubject.parseUuidOrNull(null))
    }

    @Test
    fun blankIsRejected() {
        assertNull(PeerJwtSubject.parseUuidOrNull(""))
        assertNull(PeerJwtSubject.parseUuidOrNull("   "))
    }

    @Test
    fun nonUuidTextIsRejected() {
        assertNull(PeerJwtSubject.parseUuidOrNull("not-a-uuid"))
    }
}
