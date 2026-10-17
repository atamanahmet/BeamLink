package com.atamanahmet.beamlink.nexus.exception;

import lombok.Getter;

/**
 * Chunk HTTP response was not OK. Carries the status code.
 */
@Getter
public class ChunkHttpException extends FileTransferException {

    private final int statusCode;

    public ChunkHttpException(int statusCode, String message) {
        super(message, null);
        this.statusCode = statusCode;
    }
}
