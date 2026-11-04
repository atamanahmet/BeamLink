package com.beamlink.peer.security

import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Trusts only a nexus leaf whose SHA-256 pin matches. Pin replaces hostname checks.
 */
class NexusTlsPin(
    private val pin: () -> String?,
) : X509TrustManager, HostnameVerifier {

    /**
     * True when a pin is stored.
     */
    fun hasPin(): Boolean {
        return !pin().isNullOrBlank()
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("client certs are not accepted")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        PinnedLeafTrust.accept(chain, pin, MISSING_PIN, MISMATCH)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }

    override fun verify(hostname: String, session: SSLSession): Boolean {
        return try {
            val peer = session.peerCertificates
            if (peer.isEmpty() || peer[0] !is X509Certificate) {
                false
            } else {
                val stored = pin()
                stored != null && stored == NexusTlsFingerprint.of(peer[0] as X509Certificate)
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val MISSING_PIN = "Nexus TLS pin is not confirmed"
        const val MISMATCH = "Nexus TLS pin mismatch"
        const val PROBE_CONNECT_TIMEOUT_MS = 5_000
        const val PROBE_READ_TIMEOUT_MS = 5_000

        /**
         * TLS handshake only. Returns the leaf fingerprint. No HTTP and no store.
         * Connect and read each wait at most 5 s.
         */
        fun probe(host: String, port: Int): String {
            try {
                val leaf = AtomicReference<X509Certificate>()
                val capture = capturingManager(leaf)
                val ctx = SSLContext.getInstance("TLS")
                ctx.init(null, arrayOf(capture), SecureRandom())
                val socket = ctx.socketFactory.createSocket() as SSLSocket
                socket.soTimeout = PROBE_READ_TIMEOUT_MS
                socket.use {
                    val params = it.sslParameters
                    params.endpointIdentificationAlgorithm = null
                    it.sslParameters = params
                    it.connect(InetSocketAddress(host, port), PROBE_CONNECT_TIMEOUT_MS)
                    it.startHandshake()
                }
                val cert = leaf.get() ?: throw IllegalStateException("Connect got no certificate")
                return NexusTlsFingerprint.of(cert)
            } catch (e: Exception) {
                throw IllegalStateException(ProbeFailure.message(e), e)
            }
        }

        /**
         * Accepts only a leaf whose fingerprint equals the pin supplier.
         */
        fun acceptPinnedLeaf(chain: Array<X509Certificate>?, pinSupplier: () -> String?) {
            PinnedLeafTrust.accept(chain, pinSupplier, MISSING_PIN, MISMATCH)
        }

        private fun capturingManager(leaf: AtomicReference<X509Certificate>): X509TrustManager {
            return object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.isEmpty()) {
                        throw CertificateException("empty certificate chain")
                    }
                    leaf.set(chain[0])
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return emptyArray()
                }
            }
        }
    }
}
