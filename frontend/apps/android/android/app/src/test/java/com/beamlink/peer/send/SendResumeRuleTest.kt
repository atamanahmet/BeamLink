package com.beamlink.peer.send

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Skip receive register only when a transfer id already exists.
 */
class SendResumeRuleTest {

    @Test
    fun skipRegisterWhenTransferIdPresent() {
        assertTrue(SendResumeRule.skipRegister(UUID.randomUUID()))
        assertFalse(SendResumeRule.skipRegister(null))
    }
}
