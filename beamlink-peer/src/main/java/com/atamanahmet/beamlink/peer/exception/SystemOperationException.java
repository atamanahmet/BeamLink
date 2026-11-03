package com.atamanahmet.beamlink.peer.exception;

public class SystemOperationException extends RuntimeException {

    public SystemOperationException(String message) {
        super(message);
    }

    public SystemOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}