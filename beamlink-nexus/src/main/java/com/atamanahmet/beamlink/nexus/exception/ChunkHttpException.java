package com.atamanahmet.beamlink.nexus.exception;

import lombok.Getter;

/**
 * Chunk HTTP response was not OK. Carries the status code.
 */
@Getter
public class ChunkHttpException extends FileTransferException {

    private final int statusCode;
    private final String code;

    public ChunkHttpException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    public ChunkHttpException(int statusCode, String message, String code) {
        super(message, null);
        this.statusCode = statusCode;
        this.code = code;
    }
}
