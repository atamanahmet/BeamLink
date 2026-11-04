package com.beamlink.peer.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Known SHA-256 hex of abc.
 */
class FileChecksumTest {

    @Test
    fun sha256OfAbc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileChecksum.sha256Hex("abc".toByteArray()),
        )
    }
}
