package com.atamanahmet.beamlink.agent.exception;

/**
 * Thrown when chunk send retries are used up.
 */
public class ChunkRetryExhaustedException extends FileTransferException {

    public ChunkRetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
