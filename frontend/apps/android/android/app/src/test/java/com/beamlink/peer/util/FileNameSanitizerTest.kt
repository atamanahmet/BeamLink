package com.beamlink.peer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure file name sanitizer cases.
 */
class FileNameSanitizerTest {

    @Test
    fun blankFallsBack() {
        assertEquals("file", FileNameSanitizer.sanitize(""))
        assertEquals("file", FileNameSanitizer.sanitize("   "))
        assertEquals("file", FileNameSanitizer.sanitize(null))
    }

    @Test
    fun stripsPathAndDots() {
        assertEquals("note.txt", FileNameSanitizer.sanitize("../dir/./note.txt"))
        assertEquals("note.txt", FileNameSanitizer.sanitize("..\\dir\\note.txt"))
    }

    @Test
    fun dropsControlChars() {
        assertEquals("ab", FileNameSanitizer.sanitize("a\u0001b"))
        assertEquals("ab", FileNameSanitizer.sanitize("a\nb"))
    }

    @Test
    fun reservedNamesFallBack() {
        assertEquals("file", FileNameSanitizer.sanitize("CON"))
        assertEquals("file", FileNameSanitizer.sanitize("con.txt"))
        assertEquals("file", FileNameSanitizer.sanitize("NUL"))
        assertEquals("file", FileNameSanitizer.sanitize("COM1.log"))
        assertEquals("file", FileNameSanitizer.sanitize("LPT9"))
    }

    @Test
    fun trimsVeryLongNames() {
        val longName = "a".repeat(250) + ".txt"
        val out = FileNameSanitizer.sanitize(longName)
        assertTrue(out.length <= FileNameSanitizer.MAX_NAME_LENGTH)
        assertTrue(out.endsWith(".txt"))
    }

    @Test
    fun uniqueAddsSuffix() {
        val taken = setOf("a.txt")
        assertEquals("a-1.txt", FileNameSanitizer.unique("a.txt") { taken.contains(it) })
    }

    @Test
    fun uniqueSkipsTakenSuffixes() {
        val taken = setOf("a.txt", "a-1.txt", "a-2.txt")
        assertEquals("a-3.txt", FileNameSanitizer.unique("a.txt") { taken.contains(it) })
    }

    @Test
    fun uniqueIgnoresPathCollisionInput() {
        val taken = mutableSetOf<String>()
        val first = FileNameSanitizer.unique("../x.txt") { taken.contains(it) }
        taken.add(first)
        val second = FileNameSanitizer.unique("..\\x.txt") { taken.contains(it) }
        assertEquals("x.txt", first)
        assertEquals("x-1.txt", second)
        assertFalse(first.contains("/"))
        assertFalse(second.contains("\\"))
    }
}
