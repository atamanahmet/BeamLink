package com.beamlink.peer.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Pure target device id UUID parse cases.
 */
class TargetDeviceIdTest {

    @Test
    fun validUuid() {
        val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
        assertEquals(id, TargetDeviceId.parseUuidOrNull(id.toString()))
    }

    @Test
    fun nullIsRejected() {
        assertNull(TargetDeviceId.parseUuidOrNull(null))
    }

    @Test
    fun blankIsRejected() {
        assertNull(TargetDeviceId.parseUuidOrNull(""))
        assertNull(TargetDeviceId.parseUuidOrNull("   "))
    }

    @Test
    fun nonUuidTextIsRejected() {
        assertNull(TargetDeviceId.parseUuidOrNull("not-a-uuid"))
    }
}
