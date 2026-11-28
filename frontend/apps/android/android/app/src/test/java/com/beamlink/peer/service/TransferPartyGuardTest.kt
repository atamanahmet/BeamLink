package com.beamlink.peer.service

import com.beamlink.peer.exception.ForbiddenException
import org.junit.Test
import java.util.UUID

/**
 * Pure party guard cases for source and party checks.
 */
class TransferPartyGuardTest {

    private val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val b = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun callerEqualsSourcePasses() {
        TransferPartyGuard.requireSource(a, a.toString())
    }

    @Test(expected = ForbiddenException::class)
    fun differentUuidIsForbidden() {
        TransferPartyGuard.requireSource(a, b.toString())
    }

    @Test(expected = ForbiddenException::class)
    fun nullCallerIsForbidden() {
        TransferPartyGuard.requireSource(null, a.toString())
    }

    @Test(expected = ForbiddenException::class)
    fun nullSourceIsForbidden() {
        TransferPartyGuard.requireSource(a, null)
    }

    @Test(expected = ForbiddenException::class)
    fun blankSourceIsForbidden() {
        TransferPartyGuard.requireSource(a, "   ")
    }

    @Test(expected = ForbiddenException::class)
    fun nonUuidSourceIsForbidden() {
        TransferPartyGuard.requireSource(a, "not-a-uuid")
    }

    @Test
    fun requirePartySourceMatchPasses() {
        TransferPartyGuard.requireParty(a, a.toString(), b.toString())
    }

    @Test
    fun requirePartyTargetMatchPasses() {
        TransferPartyGuard.requireParty(a, b.toString(), a.toString())
    }

    @Test(expected = ForbiddenException::class)
    fun requirePartyNeitherIsForbidden() {
        TransferPartyGuard.requireParty(a, b.toString(), b.toString())
    }
}
