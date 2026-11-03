package com.atamanahmet.beamlink.peer.exception;

import io.netty.channel.ConnectTimeoutException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Maps a throwable chain to ErrorReason. Pure, no Spring.
 */
public final class ErrorReasonClassifier {

    private ErrorReasonClassifier() {
    }

    /**
     * Walks the cause chain. Root type wins. English wrap text is last fallback.
     */
    public static ErrorReason classify(Throwable error) {
        if (error == null) {
            return ErrorReason.UNKNOWN;
        }
        List<Throwable> chain = ExceptionUtils.getThrowableList(error);
        for (int i = chain.size() - 1; i >= 0; i--) {
            ErrorReason typed = classifyType(chain.get(i));
            if (typed != ErrorReason.UNKNOWN) {
                return typed;
            }
        }
        for (Throwable t : chain) {
            ErrorReason fromMessage = classifyMessage(t);
            if (fromMessage != ErrorReason.UNKNOWN) {
                return fromMessage;
            }
        }
        return ErrorReason.UNKNOWN;
    }

    /**
     * Type-based reason for one throwable, ignoring its cause.
     */
    private static ErrorReason classifyType(Throwable t) {
        if (t instanceof CertificateException
                || t instanceof CertPathValidatorException
                || t instanceof CertPathBuilderException
                || t instanceof SSLPeerUnverifiedException) {
            return ErrorReason.TLS_TRUST;
        }
        if (t instanceof SSLException) {
            return isTrustMessage(t.getMessage()) ? ErrorReason.TLS_TRUST : ErrorReason.TLS_OTHER;
        }
        if (t instanceof HttpConnectTimeoutException || t instanceof ConnectTimeoutException) {
            return ErrorReason.CONNECT_TIMEOUT;
        }
        if (t instanceof SocketTimeoutException) {
            return ErrorReason.TIMEOUT;
        }
        if (t instanceof HttpTimeoutException || t instanceof TimeoutException) {
            return ErrorReason.TIMEOUT;
        }
        if (t instanceof UnknownHostException || t instanceof UnresolvedAddressException) {
            return ErrorReason.UNKNOWN_HOST;
        }
        if (t instanceof ConnectException) {
            return ErrorReason.REFUSED;
        }
        if (t instanceof ClosedChannelException) {
            return ErrorReason.REFUSED;
        }
        return ErrorReason.UNKNOWN;
    }

    /**
     * English wrap text when the chain has no typed match. Never CONNECT_TIMEOUT.
     */
    private static ErrorReason classifyMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            return ErrorReason.UNKNOWN;
        }
        if (message.startsWith("Timed out registering") || message.startsWith("Timed out querying")) {
            return ErrorReason.TIMEOUT;
        }
        if (message.contains("Cannot reach target") || message.contains("Cannot reach peer")) {
            return ErrorReason.REFUSED;
        }
        return ErrorReason.UNKNOWN;
    }

    /**
     * True when an SSL message is a trust or pin failure.
     */
    private static boolean isTrustMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("pkix")
                || lower.contains("certificate")
                || lower.contains("certpath")
                || lower.contains("fingerprint")
                || lower.contains("not trusted")
                || lower.contains("unable to find valid certification");
    }
}
