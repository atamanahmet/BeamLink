package com.beamlink.peer.receive

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import java.io.FileOutputStream
import java.net.URLConnection
import java.nio.ByteBuffer

/**
 * Pending Downloads rows with positional writes.
 */
class MediaStorePublisher(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /**
     * Inserts a pending Downloads row and returns its URI.
     */
    fun createPending(fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(fileName))
            put(MediaStore.Downloads.IS_PENDING, 1)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Cannot create Downloads row")
    }

    /**
     * Writes bytes at offset through the row descriptor.
     */
    fun writeAt(uri: Uri, offset: Long, bytes: ByteArray) {
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                channel.position(offset)
                val buf = ByteBuffer.wrap(bytes)
                while (buf.hasRemaining()) {
                    channel.write(buf)
                }
            }
        } ?: throw java.io.IOException("Cannot open Downloads descriptor")
    }

    /**
     * Clears IS_PENDING so the file is visible.
     */
    fun complete(uri: Uri) {
        val done = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        resolver.update(uri, done, null, null)
    }

    /**
     * True when this pending URI still exists.
     */
    fun existsPending(uri: Uri): Boolean {
        queryPending(uri, arrayOf(MediaStore.Downloads._ID))?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    /**
     * Deletes a pending or finished Downloads row.
     */
    fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    /**
     * True when Downloads already has this display name, including pending.
     */
    fun exists(fileName: String): Boolean {
        queryPending(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(fileName),
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    /**
     * Deletes pending Downloads rows that are not in liveUris.
     */
    fun deletePendingExcept(liveUris: Set<Uri>) {
        val ids = ArrayList<Long>()
        queryPending(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.IS_PENDING}=?",
            arrayOf("1"),
        )?.use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idx))
            }
        }
        for (id in ids) {
            val uri = ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                id,
            )
            if (uri !in liveUris) {
                resolver.delete(uri, null, null)
            }
        }
    }

    /**
     * Queries including pending rows. API 29 setIncludePending, API 30 QUERY_ARG.
     */
    private fun queryPending(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
    ): android.database.Cursor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val extras = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                if (selection != null) {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        selectionArgs,
                    )
                }
            }
            resolver.query(uri, projection, extras, null)
        } else {
            @Suppress("DEPRECATION")
            val pendingUri = MediaStore.setIncludePending(uri)
            resolver.query(pendingUri, projection, selection, selectionArgs, null)
        }
    }

    private fun mimeOf(fileName: String): String {
        return URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
    }
}
