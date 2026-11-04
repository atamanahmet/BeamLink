package com.beamlink.peer.receive

import com.beamlink.peer.exception.InvalidRequestException

/**
 * Parses Content-Range bytes start-end/total like the desktop receiver.
 */
object ContentRangeParser {

    data class Range(
        val start: Long,
        val length: Long,
        val total: Long,
    )

    /**
     * Returns start offset and length. Throws on bad header.
     */
    fun parse(contentRange: String?): Range {
        if (contentRange.isNullOrBlank()) {
            throw InvalidRequestException("Invalid Content-Range header: $contentRange")
        }
        try {
            val bytesPart = contentRange.replace("bytes ", "")
            val dash = bytesPart.indexOf('-')
            val slash = bytesPart.indexOf('/')
            if (dash < 0 || slash <= dash) {
                throw InvalidRequestException("Invalid Content-Range header: $contentRange")
            }
            val start = bytesPart.substring(0, dash).trim().toLong()
            val end = bytesPart.substring(dash + 1, slash).trim().toLong()
            val total = bytesPart.substring(slash + 1).trim().toLong()
            val length = end - start + 1L
            if (length < 0L || start < 0L) {
                throw InvalidRequestException("Invalid Content-Range length: $contentRange")
            }
            return Range(start, length, total)
        } catch (e: InvalidRequestException) {
            throw e
        } catch (e: Exception) {
            throw InvalidRequestException("Invalid Content-Range header: $contentRange", e)
        }
    }
}
