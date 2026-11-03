package com.atamanahmet.beamlink.nexus.exception;

public class FileTransferException extends RuntimeException {

    private final String targetHost;

    public FileTransferException(String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * Wraps a transfer HTTP failure. targetHost is the peer IP when known.
     */
    public FileTransferException(String message, Throwable cause, String targetHost) {
        super(message, cause);
        this.targetHost = targetHost;
    }

    /**
     * Peer IP used for loopback exclusion, or null.
     */
    public String getTargetHost() {
        return targetHost;
    }
}
