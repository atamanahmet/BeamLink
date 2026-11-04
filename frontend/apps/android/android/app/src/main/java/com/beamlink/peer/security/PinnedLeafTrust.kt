package com.beamlink.peer.security

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * Trusts a leaf whose SHA-256 fingerprint matches the pin supplier.
 */
class PinnedLeafTrust(
    private val pin: () -> String?,
    private val missingMessage: String,
    private val mismatchMessage: String,
) : X509TrustManager, HostnameVerifier {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("client certs are not accepted")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        accept(chain, pin, missingMessage, mismatchMessage)
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
        /**
         * Accepts only a leaf whose fingerprint equals the pin supplier.
         */
        fun accept(
            chain: Array<X509Certificate>?,
            pinSupplier: () -> String?,
            missingMessage: String,
            mismatchMessage: String,
        ) {
            val stored = pinSupplier()
            if (stored.isNullOrBlank()) {
                throw CertificateException(missingMessage)
            }
            if (chain == null || chain.isEmpty()) {
                throw CertificateException("empty certificate chain")
            }
            val seen = NexusTlsFingerprint.of(chain[0])
            if (stored != seen) {
                throw CertificateException("$mismatchMessage expected=$stored seen=$seen")
            }
        }
    }
}
