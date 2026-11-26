package com.atamanahmet.beamlink.peer.exception;

public class ForbiddenException extends RuntimeException {
    private final String code;

    public ForbiddenException(String message) {
        this(message, "FORBIDDEN");
    }

    public ForbiddenException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
