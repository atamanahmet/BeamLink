package com.beamlink.peer.send

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Per-call EventListener. State lives in ConnectPhaseStore until callEnd or callFailed.
 */
class ConnectPhaseListener(
    private val call: Call,
    private val store: ConnectPhaseStore,
) : EventListener() {

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        store.create(this.call).markConnectFailed()
    }

    override fun callEnd(call: Call) {
        store.remove(this.call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        store.remove(this.call)
    }

    companion object Factory : EventListener.Factory {
        private val store = ConnectPhaseStore()

        override fun create(call: Call): EventListener {
            return ConnectPhaseListener(call, store)
        }

        /**
         * Reads the entry for this call without removing it.
         */
        fun get(call: Call): ConnectPhaseStore.Entry? {
            return store.get(call)
        }
    }
}
