package com.atamanahmet.beamlink.agent.exception;

public class SystemOperationException extends RuntimeException {

    public SystemOperationException(String message) {
        super(message);
    }

    public SystemOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}