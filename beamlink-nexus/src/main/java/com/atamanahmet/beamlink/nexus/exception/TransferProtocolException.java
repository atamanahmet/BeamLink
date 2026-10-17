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
        this(code, message, status, null);
    }

    public TransferProtocolException(
            String code, String message, HttpStatus status, PauseOwner pausedBy) {
        super(message);
        this.code = code;
        this.status = status;
        this.pausedBy = pausedBy;
    }
}
