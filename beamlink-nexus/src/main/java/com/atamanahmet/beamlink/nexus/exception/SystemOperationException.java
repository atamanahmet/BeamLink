package com.atamanahmet.beamlink.nexus.exception;

public class SystemOperationException extends RuntimeException {

    public SystemOperationException(String message) {
        super(message);
    }

    public SystemOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}