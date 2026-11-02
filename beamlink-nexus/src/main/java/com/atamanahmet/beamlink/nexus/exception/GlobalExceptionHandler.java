package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.dto.EnrollmentErrorBody;
import com.atamanahmet.beamlink.nexus.service.DeviceEnrollmentService;
import com.atamanahmet.beamlink.nexus.service.FileSizeRule;
import com.atamanahmet.beamlink.nexus.service.FileTransferErrorRule;
import tools.jackson.databind.DatabindException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Enrollment register/challenge expected errors. Body stays {error, interval}.
     */
    @ExceptionHandler(DeviceAlreadyExistsException.class)
    public ResponseEntity<EnrollmentErrorBody> handleDeviceAlreadyExists(DeviceAlreadyExistsException ex) {
        ErrorReason reason = ErrorReasonClassifier.classify(ex);
        ErrorLog.log(log, HttpStatus.CONFLICT, "access_denied", reason, "Device already exists", ex);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(EnrollmentErrorBody.of("access_denied"));
    }

    /**
     * Enrollment register/challenge expected errors.
     */
    @ExceptionHandler(DeviceEnrollmentService.EnrollmentDeniedException.class)
    public ResponseEntity<EnrollmentErrorBody> handleEnrollmentDenied(
            DeviceEnrollmentService.EnrollmentDeniedException ex) {
        ErrorReason reason = ErrorReasonClassifier.classify(ex);
        ErrorLog.log(log, ex.getStatus(), ex.getError(), reason, ex.getError(), ex);
        return ResponseEntity.status(ex.getStatus()).body(EnrollmentErrorBody.of(ex.getError()));
    }

    @ExceptionHandler(NameAlreadyInUseException.class)
    public ResponseEntity<ProblemDetail> handleNameAlreadyInUse(NameAlreadyInUseException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "Name in use",
                "Name is already in use",
                "NAME_IN_USE",
                ex);
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDeviceNotFound(DeviceNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Device not found",
                "Device not found",
                "DEVICE_NOT_FOUND",
                ex);
    }

    @ExceptionHandler(InsufficientDiskSpaceException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientDiskSpace(InsufficientDiskSpaceException ex) {
        return problem(
                HttpStatus.INSUFFICIENT_STORAGE,
                "Insufficient storage",
                "Insufficient disk space",
                "INSUFFICIENT_STORAGE",
                ex);
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
     * Unreachable target is 502, timeout is 504. Other transfer errors stay 500.
     */
    @ExceptionHandler(FileTransferException.class)
    public ResponseEntity<ProblemDetail> handleFileTransfer(FileTransferException ex) {
        if (FileTransferErrorRule.isTargetUnreachable(ex)) {
            ErrorReason reason = FileTransferErrorRule.reason(ex);
            HttpStatus status = reason == ErrorReason.TIMEOUT || reason == ErrorReason.CONNECT_TIMEOUT
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.BAD_GATEWAY;
            return problem(
                    status,
                    "Target unreachable",
                    FileTransferErrorRule.TARGET_UNREACHABLE_MESSAGE,
                    FileTransferErrorRule.TARGET_UNREACHABLE,
                    ex);
        }
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "File transfer failed",
                "File transfer failed",
                "FILE_TRANSFER_FAILED",
                ex);
    }

    /**
     * Missing route is 404. No stack.
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            @NonNull NoResourceFoundException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ResponseEntity<ProblemDetail> body = problem(
                HttpStatus.NOT_FOUND,
                "Not found",
                "Not found",
                "NOT_FOUND",
                ex);
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
            body = handleTransferProtocol(t);
        } else if (isFileSizeUnreadable(e)) {
            body = handleTransferProtocol(FileSizeRule.reject(null));
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
    public ResponseEntity<ProblemDetail> handleTransferProtocol(TransferProtocolException ex) {
        ErrorReason reason = ErrorReasonClassifier.classify(ex);
        ErrorLog.log(log, ex.getStatus(), ex.getCode(), reason, ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetails.of(
                ex.getStatus(),
                "Transfer protocol error",
                ex.getMessage(),
                ex.getCode(),
                reason);
        if (ex.getPausedBy() != null) {
            pd.setProperty("pausedBy", ex.getPausedBy().name());
        }
        return ResponseEntity.status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
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

    @ExceptionHandler(OffsetMismatchException.class)
    public ResponseEntity<ProblemDetail> handleOffsetMismatch(OffsetMismatchException ex) {
        ErrorReason reason = ErrorReasonClassifier.classify(ex);
        ErrorLog.log(log, HttpStatus.CONFLICT, "OFFSET_MISMATCH", reason, "Unexpected offset", ex);
        ProblemDetail pd = ProblemDetails.of(
                HttpStatus.CONFLICT,
                "Offset mismatch",
                "Unexpected offset",
                "OFFSET_MISMATCH",
                reason);
        pd.setProperty("expectedOffset", ex.getExpectedOffset());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(ResetBusyException.class)
    public ResponseEntity<ProblemDetail> handleResetBusy(ResetBusyException ex) {
        ErrorReason reason = ErrorReasonClassifier.classify(ex);
        ErrorLog.log(log, HttpStatus.CONFLICT, "RESET_BUSY", reason, ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetails.of(
                HttpStatus.CONFLICT,
                "Reset busy",
                ex.getMessage(),
                "RESET_BUSY",
                reason);
        pd.setProperty("count", ex.getCount());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ProblemDetail> handleIOException(IOException ex) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "IO error",
                "File operation failed",
                "IO_ERROR",
                ex);
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
