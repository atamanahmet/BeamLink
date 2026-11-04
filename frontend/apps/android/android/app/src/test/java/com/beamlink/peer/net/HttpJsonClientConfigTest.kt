package com.beamlink.peer.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Interceptor
import okhttp3.Request

/**
 * Pure OkHttp client and AddressGuardInterceptor checks. No sockets.
 */
class HttpJsonClientConfigTest {

    @Test
    fun sharedClientDisablesRedirects() {
        val client = HttpJsonClient.buildSharedClient()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun sharedClientHasAddressGuardInterceptor() {
        val client = HttpJsonClient.buildSharedClient()
        val found = client.interceptors.any { it is AddressGuardInterceptor }
        assertTrue(found)
    }

    @Test
    fun interceptorRefusesPublicHostWithoutProceed() {
        val interceptor = AddressGuardInterceptor()
        val request = Request.Builder().url("http://8.8.8.8:9090/x").build()
        assertThrows(IllegalArgumentException::class.java) {
            interceptor.intercept(RefuseChain(request))
        }
    }

    /**
     * Chain stub that fails if proceed is reached.
     */
    private class RefuseChain(
        private val request: Request,
    ) : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): okhttp3.Response {
            throw AssertionError("proceed must not run for refused host")
        }

        override fun connection(): okhttp3.Connection? = null

        override fun call(): okhttp3.Call {
            throw UnsupportedOperationException("unused")
        }

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
            this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
            this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain =
            this
    }
}
