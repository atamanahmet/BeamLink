package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.exception.ChunkHttpException;
import com.atamanahmet.beamlink.peer.exception.OffsetMismatchException;
import com.atamanahmet.beamlink.peer.exception.TransferProtocolException;

/**
 * Classifies send failures as OFFLINE, RETRY, RESYNC, CHUNK_CORRUPT, or FATAL.
 */
public final class SendFailureRule {

    private SendFailureRule() {}

    /**
     * Picks OFFLINE, RETRY, RESYNC, CHUNK_CORRUPT, or FATAL from the error cause chain.
     */
    public static SendFailureKind classify(Throwable error) {
        if (error == null) {
            return SendFailureKind.RETRY;
        }
        if (PeerUnreachable.isPeerUnreachable(error)) {
            return SendFailureKind.OFFLINE;
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof OffsetMismatchException) {
                return SendFailureKind.RESYNC;
            }
            if (t instanceof TransferProtocolException protocol) {
                if ("CHUNK_CHECKSUM_MISMATCH".equals(protocol.getCode())) {
                    return SendFailureKind.CHUNK_CORRUPT;
                }
                return SendFailureKind.FATAL;
            }
            if (t instanceof ChunkHttpException httpError) {
                if (httpError.getStatusCode() == 422) {
                    return SendFailureKind.CHUNK_CORRUPT;
                }
                return classifyHttpStatus(httpError.getStatusCode());
            }
        }
        return SendFailureKind.RETRY;
    }

    /**
     * True when sendChunkWithRetry should resend the same chunk.
     */
    public static boolean allowsChunkRetry(SendFailureKind kind) {
        return kind == SendFailureKind.RETRY
                || kind == SendFailureKind.OFFLINE
                || kind == SendFailureKind.CHUNK_CORRUPT;
    }

    private static SendFailureKind classifyHttpStatus(int status) {
        if (DiskPauseRule.senderNoRetry(status)) {
            return SendFailureKind.FATAL;
        }
        if (status == 408 || status == 429 || (status >= 500 && status <= 599)) {
            return SendFailureKind.RETRY;
        }
        if (status >= 400 && status <= 499) {
            return SendFailureKind.FATAL;
        }
        return SendFailureKind.RETRY;
    }
}
