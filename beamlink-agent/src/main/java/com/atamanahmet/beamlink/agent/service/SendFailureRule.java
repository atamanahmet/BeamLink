package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.exception.ChunkHttpException;
import com.atamanahmet.beamlink.agent.exception.TransferProtocolException;

/**
 * Classifies send failures as OFFLINE, RETRY, or FATAL.
 */
public final class SendFailureRule {

    private SendFailureRule() {}

    /**
     * Picks OFFLINE, RETRY, or FATAL from the error cause chain.
     */
    public static SendFailureKind classify(Throwable error) {
        if (error == null) {
            return SendFailureKind.RETRY;
        }
        if (PeerUnreachable.isPeerUnreachable(error)) {
            return SendFailureKind.OFFLINE;
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TransferProtocolException) {
                return SendFailureKind.FATAL;
            }
            if (t instanceof ChunkHttpException httpError) {
                return classifyHttpStatus(httpError.getStatusCode());
            }
            if (isChecksumMessage(t.getMessage())) {
                return SendFailureKind.FATAL;
            }
        }
        return SendFailureKind.RETRY;
    }

    private static SendFailureKind classifyHttpStatus(int status) {
        if (status == 408 || status == 429 || (status >= 500 && status <= 599)) {
            return SendFailureKind.RETRY;
        }
        if (status >= 400 && status <= 499) {
            return SendFailureKind.FATAL;
        }
        return SendFailureKind.RETRY;
    }

    private static boolean isChecksumMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.toLowerCase().contains("checksum");
    }
}
