package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.ErrorReasonClassifier;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import com.atamanahmet.beamlink.peer.security.LoopbackAddresses;

/**
 * Maps file transfer failures to the unreachable target case.
 */
public final class FileTransferErrorRule {

    public static final String TARGET_UNREACHABLE = "TARGET_UNREACHABLE";
    public static final String TARGET_UNREACHABLE_MESSAGE =
            "Target peer is unreachable or has receive turned off";

    private FileTransferErrorRule() {
    }

    /**
     * Classifies the error, then drops CONNECT_TIMEOUT on loopback to TIMEOUT.
     */
    public static ErrorReason reason(Throwable error) {
        ErrorReason raw = ErrorReasonClassifier.classify(error);
        if (raw != ErrorReason.CONNECT_TIMEOUT) {
            return raw;
        }
        if (LoopbackAddresses.isLoopback(targetHost(error))) {
            return ErrorReason.TIMEOUT;
        }
        return raw;
    }

    /**
     * True when the peer is down, TLS fails, or the register call could not reach it.
     */
    public static boolean isTargetUnreachable(Throwable error) {
        ErrorReason classified = reason(error);
        return classified == ErrorReason.TIMEOUT
                || classified == ErrorReason.CONNECT_TIMEOUT
                || classified == ErrorReason.REFUSED
                || classified == ErrorReason.UNKNOWN_HOST
                || classified == ErrorReason.TLS_TRUST
                || classified == ErrorReason.TLS_OTHER;
    }

    /**
     * Reads targetHost from the first FileTransferException in the chain.
     */
    private static String targetHost(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof FileTransferException wrap && wrap.getTargetHost() != null) {
                return wrap.getTargetHost();
            }
        }
        return null;
    }
}
