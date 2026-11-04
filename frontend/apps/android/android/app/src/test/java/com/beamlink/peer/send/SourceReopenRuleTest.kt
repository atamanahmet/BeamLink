package com.beamlink.peer.send

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Open failures and a missing stream end the send. Other errors do not.
 */
class SourceReopenRuleTest {

    @Test
    fun missingStreamIsGone() {
        assertTrue(SourceReopenRule.cannotReopen(null, null))
        assertFalse(
            SourceReopenRule.cannotReopen(ByteArrayInputStream(ByteArray(0)), null),
        )
    }

    @Test
    fun openGrantAndIoErrorsAreGone() {
        assertTrue(SourceReopenRule.cannotReopen(null, SecurityException("denied")))
        assertTrue(SourceReopenRule.cannotReopen(null, FileNotFoundException("gone")))
        assertTrue(SourceReopenRule.cannotReopen(null, IOException("io")))
        assertFalse(SourceReopenRule.cannotReopen(null, IllegalStateException("other")))
    }
}
