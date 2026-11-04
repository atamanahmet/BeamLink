package com.beamlink.peer.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Runs AddressGuard on every request host before the connection opens.
 */
class AddressGuardInterceptor : Interceptor {

    /**
     * Refuses non local or non private hosts, then proceeds.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        AddressGuard.requireAllowedHost(host)
        return chain.proceed(chain.request())
    }
}
