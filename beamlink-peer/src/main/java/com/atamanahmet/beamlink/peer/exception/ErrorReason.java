package com.atamanahmet.beamlink.peer.exception;

/**
 * Stable network and TLS failure class for logs and ProblemDetail.
 */
public enum ErrorReason {
    TIMEOUT,
    CONNECT_TIMEOUT,
    REFUSED,
    UNKNOWN_HOST,
    TLS_TRUST,
    TLS_OTHER,
    UNKNOWN
}
