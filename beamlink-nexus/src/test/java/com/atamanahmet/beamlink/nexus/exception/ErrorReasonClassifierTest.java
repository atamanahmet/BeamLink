package com.atamanahmet.beamlink.nexus.exception;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertificateException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorReasonClassifierTest {

    @Test
    void connectTimeoutIsHttpConnectTimeout() {
        assertThat(ErrorReasonClassifier.classify(new HttpConnectTimeoutException("connect")))
                .isEqualTo(ErrorReason.CONNECT_TIMEOUT);
    }

    @Test
    void nestedConnectTimeoutIsConnectTimeout() {
        Throwable nested = new FileTransferException(
                "wrap",
                new RuntimeException("mid", new HttpConnectTimeoutException("connect")));
        assertThat(ErrorReasonClassifier.classify(nested)).isEqualTo(ErrorReason.CONNECT_TIMEOUT);
    }

    @Test
    void refusedIsConnectExceptionIgnoringMessage() {
        assertThat(ErrorReasonClassifier.classify(new ConnectException("Connection refused")))
                .isEqualTo(ErrorReason.REFUSED);
        assertThat(ErrorReasonClassifier.classify(new ConnectException("Connection timed out")))
                .isEqualTo(ErrorReason.REFUSED);
        assertThat(ErrorReasonClassifier.classify(new ConnectException("Zeitüberschreitung")))
                .isEqualTo(ErrorReason.REFUSED);
    }

    @Test
    void readTimeoutStaysTimeout() {
        assertThat(ErrorReasonClassifier.classify(new SocketTimeoutException("read timed out")))
                .isEqualTo(ErrorReason.TIMEOUT);
        assertThat(ErrorReasonClassifier.classify(new SocketTimeoutException("connect timed out")))
                .isEqualTo(ErrorReason.TIMEOUT);
        assertThat(ErrorReasonClassifier.classify(new HttpTimeoutException("t")))
                .isEqualTo(ErrorReason.TIMEOUT);
    }

    @Test
    void unknownIsPlainRuntime() {
        assertThat(ErrorReasonClassifier.classify(new IllegalStateException("other")))
                .isEqualTo(ErrorReason.UNKNOWN);
        assertThat(ErrorReasonClassifier.classify(null)).isEqualTo(ErrorReason.UNKNOWN);
    }

    @Test
    void unknownHostIsUnknownHostException() {
        assertThat(ErrorReasonClassifier.classify(new UnknownHostException("no.such.host")))
                .isEqualTo(ErrorReason.UNKNOWN_HOST);
    }

    @Test
    void tlsTrustIsCertificateException() {
        assertThat(ErrorReasonClassifier.classify(new CertificateException("PKIX path building failed")))
                .isEqualTo(ErrorReason.TLS_TRUST);
    }

    @Test
    void tlsOtherIsSslException() {
        assertThat(ErrorReasonClassifier.classify(new SSLException("handshake_failure")))
                .isEqualTo(ErrorReason.TLS_OTHER);
    }

    @Test
    void chainedWrapUsesRootCause() {
        Throwable chained = new FileTransferException(
                "wrap",
                new RuntimeException("mid", new ConnectException("Connection refused")));
        assertThat(ErrorReasonClassifier.classify(chained)).isEqualTo(ErrorReason.REFUSED);
    }
}
