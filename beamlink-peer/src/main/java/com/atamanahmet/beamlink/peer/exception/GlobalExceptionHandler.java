package com.atamanahmet.beamlink.peer.exception;

import com.atamanahmet.beamlink.peer.service.FileSizeRule;
import com.atamanahmet.beamlink.peer.service.FileTransferErrorRule;
import tools.jackson.databind.DatabindException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SystemOperationException.class)
    public ResponseEntity<ProblemDetail> handleSystemOperationException(SystemOperationException ex) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "System error",
                "System operation failed",
                "SYSTEM_ERROR",
                ex);
    }

    @ExceptionHandler(InsufficientDiskSpaceException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientDiskSpaceException(InsufficientDiskSpaceException e) {
        return problem(
                HttpStatus.INSUFFICIENT_STORAGE,
                "Insufficient storage",
                "Insufficient disk space",
                "INSUFFICIENT_STORAGE",
                e);
    }

    /**
     * Receiver disk below reserve. 507 INSUFFICIENT_DISK.
     */
    @ExceptionHandler(InsufficientDiskException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientDisk(InsufficientDiskException e) {
        return problem(
                HttpStatus.INSUFFICIENT_STORAGE,
                "Insufficient disk",
                e.getMessage(),
                "INSUFFICIENT_DISK",
                e);
    }

    /**
     * Unreachable peer is 502, timeout is 504. Other transfer errors stay 500.
     */
    @ExceptionHandler(FileTransferException.class)
    public ResponseEntity<ProblemDetail> handleFileTransferException(FileTransferException e) {
        if (FileTransferErrorRule.isTargetUnreachable(e)) {
            ErrorReason reason = FileTransferErrorRule.reason(e);
            HttpStatus status = reason == ErrorReason.TIMEOUT || reason == ErrorReason.CONNECT_TIMEOUT
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.BAD_GATEWAY;
            String detail = reason == ErrorReason.TLS_OTHER || reason == ErrorReason.TLS_TRUST
                    ? "TLS handshake with the peer failed"
                    : FileTransferErrorRule.TARGET_UNREACHABLE_MESSAGE;
            return problem(
                    status,
                    "Target unreachable",
                    detail,
                    FileTransferErrorRule.TARGET_UNREACHABLE,
                    e);
        }
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File transfer failed",
                "File transfer failed",
                "FILE_TRANSFER_FAILED",
                e);
    }

    /**
     * Missing route is 404. No stack.
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            @NonNull NoResourceFoundException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ResponseEntity<ProblemDetail> body = problem(
                HttpStatus.NOT_FOUND,
                "Not found",
                "Not found",
                "NOT_FOUND",
                e);
        return ResponseEntity.status(body.getStatusCode())
                .headers(body.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body.getBody());
    }

    /**
     * Bad fileSize JSON is INVALID_FILE_SIZE 400. Other unreadable bodies are 400.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ResponseEntity<ProblemDetail> body;
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof TransferProtocolException t) {
            body = handleTransferProtocolException(t);
        } else if (isFileSizeUnreadable(e)) {
            body = handleTransferProtocolException(FileSizeRule.reject(null));
        } else {
            body = problem(
                    HttpStatus.BAD_REQUEST,
                    "Bad request",
                    "Malformed request body",
                    "INVALID_REQUEST",
                    e);
        }
        return ResponseEntity.status(body.getStatusCode())
                .headers(body.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body.getBody());
    }

    /**
     * Transfer protocol errors keep their code and status.
     */
    @ExceptionHandler(TransferProtocolException.class)
    public ResponseEntity<ProblemDetail> handleTransferProtocolException(TransferProtocolException e) {
        ErrorReason reason = ErrorReasonClassifier.classify(e);
        ErrorLog.log(log, e.getStatus(), e.getCode(), reason, e.getMessage(), e);
        ProblemDetail pd = ProblemDetails.of(
                e.getStatus(),
                "Transfer protocol error",
                e.getMessage(),
                e.getCode(),
                reason);
        if (e.getPausedBy() != null) {
            pd.setProperty("pausedBy", e.getPausedBy().name());
        }
        return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(OffsetMismatchException.class)
    public ResponseEntity<ProblemDetail> handleOffsetMismatch(OffsetMismatchException e) {
        ErrorReason reason = ErrorReasonClassifier.classify(e);
        ErrorLog.log(log, HttpStatus.CONFLICT, "OFFSET_MISMATCH", reason, "Unexpected offset", e);
        ProblemDetail pd = ProblemDetails.of(
                HttpStatus.CONFLICT,
                "Offset mismatch",
                "Unexpected offset",
                "OFFSET_MISMATCH",
                reason);
        pd.setProperty("expectedOffset", e.getExpectedOffset());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ProblemDetail> handleIOException(IOException e) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "IO error",
                "File operation failed",
                "IO_ERROR",
                e);
    }

    @ExceptionHandler(NexusOfflineException.class)
    public ResponseEntity<ProblemDetail> handleNexusOfflineException(NexusOfflineException e) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Nexus offline",
                "Nexus is offline",
                "NEXUS_OFFLINE",
                e);
    }

    /**
     * Maps InvalidRequestException to 400 problem+json.
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException e) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Bad request",
                e.getMessage() != null ? e.getMessage() : "Invalid request",
                e.getCode() != null ? e.getCode() : "INVALID_REQUEST",
                e);
    }

    @ExceptionHandler(NexusTlsPinMismatchException.class)
    public ResponseEntity<ProblemDetail> handlePinMismatch(NexusTlsPinMismatchException e) {
        return problem(
                HttpStatus.CONFLICT,
                "TLS pin mismatch",
                e.getMessage(),
                "TLS_PIN_MISMATCH",
                e);
    }

    @ExceptionHandler(ResetBusyException.class)
    public ResponseEntity<ProblemDetail> handleResetBusy(ResetBusyException e) {
        ErrorReason reason = ErrorReasonClassifier.classify(e);
        ErrorLog.log(log, HttpStatus.CONFLICT, "RESET_BUSY", reason, e.getMessage(), e);
        ProblemDetail pd = ProblemDetails.of(
                HttpStatus.CONFLICT,
                "Reset busy",
                e.getMessage(),
                "RESET_BUSY",
                reason);
        pd.setProperty("count", e.getCount());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "An unexpected error occurred",
                "INTERNAL_ERROR",
                ex);
    }

    /**
     * True when Jackson failed on a fileSize field.
     */
    private static boolean isFileSizeUnreadable(HttpMessageNotReadableException e) {
        Throwable t = e.getCause();
        while (t != null) {
            if (t instanceof DatabindException jme) {
                return jme.getPath().stream().anyMatch(ref -> "fileSize".equals(ref.getPropertyName()));
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Builds ProblemDetail, logs once, and returns the status.
     */
    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            Throwable ex) {
        ErrorReason reason = FileTransferErrorRule.reason(ex);
        ErrorLog.log(log, status, code, reason, detail, ex);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetails.of(status, title, detail, code, reason));
    }
}
