package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Transfer protocol error with a stable code and HTTP status.
 */
@Getter
public class TransferProtocolException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final PauseOwner pausedBy;

    public TransferProtocolException(String code, String message, HttpStatus status) {
        this(code, message, status, (PauseOwner) null, null);
    }

    public TransferProtocolException(
            String code, String message, HttpStatus status, PauseOwner pausedBy) {
        this(code, message, status, pausedBy, null);
    }

    public TransferProtocolException(
            String code, String message, HttpStatus status, Throwable cause) {
        this(code, message, status, null, cause);
    }

    public TransferProtocolException(
            String code, String message, HttpStatus status, PauseOwner pausedBy, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.pausedBy = pausedBy;
    }
}
