package com.beamlink.peer

import android.content.Intent
import android.net.Uri
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginHandle
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Emits shared file metadata to the WebView. Does not copy bytes.
 */
@CapacitorPlugin(name = "ShareReceiver")
class ShareReceiverPlugin : Plugin() {

    /**
     * Reads share intent URIs and notifies listeners.
     */
    fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return
        }

        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) uris.add(uri)
        } else {
            @Suppress("DEPRECATION")
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) uris.addAll(list)
        }
        if (uris.isEmpty()) return

        val files = JSArray()
        val resolver = context.contentResolver
        for (uri in uris) {
            files.put(ContentUriMeta.read(resolver, uri))
        }

        val payload = JSObject()
        payload.put("files", files)
        notifyListeners("sharedFiles", payload, true)
    }

    companion object {
        /**
         * Finds the registered share plugin on the bridge.
         */
        fun from(handle: PluginHandle?): ShareReceiverPlugin? {
            return handle?.instance as? ShareReceiverPlugin
        }
    }
}
