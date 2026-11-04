package com.beamlink.peer.send

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Pure decision when a paused send cannot reopen its source URI.
 */
object SourceReopenRule {

    const val DETAIL = "Source file is no longer available. Send it again."

    /**
     * True when open threw a grant or missing-file error, or returned no stream.
     */
    fun cannotReopen(stream: InputStream?, openError: Exception?): Boolean {
        if (openError is SecurityException
            || openError is FileNotFoundException
            || openError is IOException
        ) {
            return true
        }
        if (openError != null) {
            return false
        }
        return stream == null
    }
}
