package com.beamlink.peer.util

/**
 * Makes a remote file name safe for local storage.
 */
object FileNameSanitizer {

    const val MAX_NAME_LENGTH = 200
    const val FALLBACK = "file"

    private val ILLEGAL_CHARS = charArrayOf('<', '>', ':', '"', '|', '?', '*')
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    /**
     * Drops path parts, dots, control chars, and reserved names. Falls back to file.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return FALLBACK
        }
        var name = raw.replace('\\', '/').substringAfterLast('/')
        name = name.filter { ch ->
            ch.code >= 32 && ch.code != 127 && ch !in ILLEGAL_CHARS
        }
        name = name.replace("..", "")
        name = name.trim().trimStart('.').trimEnd('.')
        if (name.isBlank() || name == "." || name == ".." || isReserved(name)) {
            return FALLBACK
        }
        if (name.length > MAX_NAME_LENGTH) {
            name = trimToMax(name)
        }
        return name.ifBlank { FALLBACK }
    }

    /**
     * Adds -n before the extension until exists returns false.
     */
    fun unique(raw: String?, exists: (String) -> Boolean): String {
        val base = sanitize(raw)
        if (!exists(base)) {
            return base
        }
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var n = 1
        while (n < 10_000) {
            val candidate = "$stem-$n$ext"
            val clipped = if (candidate.length > MAX_NAME_LENGTH) {
                trimToMax(candidate)
            } else {
                candidate
            }
            if (!exists(clipped)) {
                return clipped
            }
            n++
        }
        return FALLBACK
    }

    private fun isReserved(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        return stem.uppercase() in RESERVED
    }

    private fun trimToMax(name: String): String {
        if (name.length <= MAX_NAME_LENGTH) {
            return name
        }
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || name.length - dot > 20) {
            return name.substring(0, MAX_NAME_LENGTH)
        }
        val ext = name.substring(dot)
        val keep = MAX_NAME_LENGTH - ext.length
        if (keep <= 0) {
            return name.substring(0, MAX_NAME_LENGTH)
        }
        return name.substring(0, keep) + ext
    }
}
