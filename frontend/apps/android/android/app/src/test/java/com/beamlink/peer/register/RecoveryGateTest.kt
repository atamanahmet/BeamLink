package com.beamlink.peer.register

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryGateTest {

    @Test
    fun oneRecoveryPerGap() {
        assertTrue(RecoveryGate.canRecover(true, 40_000L, 0L))
        assertFalse(RecoveryGate.canRecover(true, 20_000L, 0L))
        assertTrue(RecoveryGate.canRecover(true, 30_000L, 0L))
    }

    @Test
    fun neverRegistersByItselfWhenNotApproved() {
        assertFalse(RecoveryGate.canRecover(false, 40_000L, 0L))
    }
}
