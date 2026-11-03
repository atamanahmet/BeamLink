package com.atamanahmet.beamlink.nexus.exception;

public class InvalidRequestException extends RuntimeException {
    private final String code;

    public InvalidRequestException(String message) {
        this(message, "INVALID_REQUEST");
    }

    public InvalidRequestException(String message, String code) {
        super(message);
        this.code = code;
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
        this.code = "INVALID_REQUEST";
    }

    public String getCode() {
        return code;
    }
}
