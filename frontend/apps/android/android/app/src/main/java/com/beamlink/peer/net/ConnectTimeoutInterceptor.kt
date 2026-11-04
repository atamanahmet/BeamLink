package com.beamlink.peer.net

import com.beamlink.peer.send.ConnectPhaseListener
import com.beamlink.peer.send.ConnectTimeoutException
import com.beamlink.peer.send.ConnectTimeoutRule
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Rethrows a typed connect timeout when the call failed in the connect phase.
 */
class ConnectTimeoutInterceptor : Interceptor {

    /**
     * Wraps a connect-phase SocketTimeoutException, else rethrows the original.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request())
        } catch (e: IOException) {
            val holder = ConnectPhaseListener.get(chain.call())
            if (holder != null
                && ConnectTimeoutRule.isConnectTimeout(e, holder.connectFailed)
            ) {
                throw ConnectTimeoutException(e)
            }
            throw e
        }
    }
}
