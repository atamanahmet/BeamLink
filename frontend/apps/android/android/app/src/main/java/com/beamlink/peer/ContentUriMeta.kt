package com.beamlink.peer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.getcapacitor.JSObject

/**
 * Reads display name and size for a content URI. Does not copy bytes.
 */
object ContentUriMeta {

    /**
     * Builds one file meta object from a content URI.
     */
    fun read(resolver: ContentResolver, uri: Uri): JSObject {
        var name = uri.lastPathSegment ?: "shared"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    val value = cursor.getString(nameIndex)
                    if (!value.isNullOrBlank()) name = value
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        val item = JSObject()
        item.put("uri", uri.toString())
        item.put("name", name)
        item.put("size", size)
        return item
    }
}
