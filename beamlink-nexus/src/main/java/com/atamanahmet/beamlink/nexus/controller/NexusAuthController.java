package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Nexus;
import com.atamanahmet.beamlink.nexus.dto.AuthStatusResponse;
import com.atamanahmet.beamlink.nexus.dto.ChangeCredentialsRequest;
import com.atamanahmet.beamlink.nexus.dto.LoginRequest;
import com.atamanahmet.beamlink.nexus.dto.NexusMeResponse;
import com.atamanahmet.beamlink.nexus.dto.RecoveryCodeResponse;
import com.atamanahmet.beamlink.nexus.dto.RegenerateRecoveryRequest;
import com.atamanahmet.beamlink.nexus.dto.ResetPasswordRequest;
import com.atamanahmet.beamlink.nexus.dto.SetupRequest;
import com.atamanahmet.beamlink.nexus.dto.SetupResponse;
import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetails;
import com.atamanahmet.beamlink.nexus.security.AdminSessionDays;
import com.atamanahmet.beamlink.nexus.security.LoopbackAddresses;
import com.atamanahmet.beamlink.nexus.security.PasswordSetupRule;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import com.atamanahmet.beamlink.nexus.service.AuditService;
import com.atamanahmet.beamlink.nexus.service.LoginFailureThrottle;
import com.atamanahmet.beamlink.nexus.service.NexusAuthService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.RecoveryCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/nexus/auth")
@RequiredArgsConstructor
public class NexusAuthController {

    private final NexusAuthService nexusAuthService;
    private final NexusService nexusService;
    private final LoginFailureThrottle loginFailureThrottle;
    private final RecoveryCodeService recoveryCodeService;
    private final AuditService auditService;

    /**
     * Returns whether first-run password setup is still needed.
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> status() {
        return ResponseEntity.ok(new AuthStatusResponse(!nexusService.hasPassword()));
    }

    /**
     * Sets the first admin password from loopback only.
     */
    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody SetupRequest request, HttpServletRequest http) {
        if (!LoopbackAddresses.isLoopback(http.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ProblemDetails.of(
                            HttpStatus.FORBIDDEN,
                            "Forbidden",
                            "Setup allowed from loopback only",
                            "FORBIDDEN",
                            ErrorReason.UNKNOWN));
        }
        if (!PasswordSetupRule.maySetup(nexusService.hasPassword())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetails.of(
                            HttpStatus.CONFLICT,
                            "Conflict",
                            "Password already set",
                            "PASSWORD_ALREADY_SET",
                            ErrorReason.UNKNOWN));
        }
        try {
            nexusAuthService.setup(request.username(), request.password());
            String recoveryCode = recoveryCodeService.issueNew();
            auditService.record(
                    AuditEventType.SETUP,
                    AuditOutcome.SUCCESS,
                    http.getRemoteAddr(),
                    null,
                    null);
            return ResponseEntity.ok(new SetupResponse(recoveryCode));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetails.of(
                            HttpStatus.CONFLICT,
                            "Conflict",
                            e.getMessage() != null ? e.getMessage() : "Password already set",
                            "PASSWORD_ALREADY_SET",
                            ErrorReason.UNKNOWN));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        String remote = http.getRemoteAddr();
        long now = System.currentTimeMillis();
        if (loginFailureThrottle.isThrottled(remote, now)) {
            int wait = loginFailureThrottle.retryAfterSeconds(remote, now);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Integer.toString(wait))
                    .body(ProblemDetails.of(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Too many requests",
                            "Too many login attempts",
                            "TOO_MANY_REQUESTS",
                            ErrorReason.UNKNOWN));
        }

        String sessionDays = AdminSessionDays.normalize(request.sessionDays());

        log.info("Login attempt for user: {}", request.username());

        if (!nexusAuthService.validateCredentials(request.username(), request.password())) {
            loginFailureThrottle.recordFailure(remote, now);
            log.warn("Failed login attempt for user: {}", request.username());
            auditService.record(
                    AuditEventType.LOGIN,
                    AuditOutcome.FAILED,
                    http.getRemoteAddr(),
                    null,
                    null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ProblemDetails.of(
                            HttpStatus.UNAUTHORIZED,
                            "Unauthorized",
                            "Invalid credentials",
                            "INVALID_CREDENTIALS",
                            ErrorReason.UNKNOWN));
        }

        loginFailureThrottle.clear(remote);
        String token = nexusAuthService.generateAdminToken(request.username(), sessionDays);
        auditService.record(
                AuditEventType.LOGIN,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Resets the admin password with the recovery code. Issues a new code once.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        String remote = http.getRemoteAddr();
        long now = System.currentTimeMillis();
        if (loginFailureThrottle.isThrottled(remote, now)) {
            int wait = loginFailureThrottle.retryAfterSeconds(remote, now);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Integer.toString(wait))
                    .body(ProblemDetails.of(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Too many requests",
                            "Too many login attempts",
                            "TOO_MANY_REQUESTS",
                            ErrorReason.UNKNOWN));
        }
        var next = recoveryCodeService.resetPassword(request.recoveryCode(), request.newPassword());
        if (next.isEmpty()) {
            loginFailureThrottle.recordFailure(remote, now);
            auditService.record(
                    AuditEventType.PASSWORD_RESET,
                    AuditOutcome.FAILED,
                    http.getRemoteAddr(),
                    null,
                    null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ProblemDetails.of(
                            HttpStatus.UNAUTHORIZED,
                            "Unauthorized",
                            "Invalid credentials",
                            "INVALID_CREDENTIALS",
                            ErrorReason.UNKNOWN));
        }
        loginFailureThrottle.clear(remote);
        auditService.record(
                AuditEventType.PASSWORD_RESET,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return ResponseEntity.ok(new RecoveryCodeResponse(next.get()));
    }

    /**
     * Issues a new recovery code. Needs the current password.
     */
    @PostMapping("/regenerate-recovery-code")
    public RecoveryCodeResponse regenerateRecoveryCode(
            @RequestBody RegenerateRecoveryRequest request, HttpServletRequest http) {
        RecoveryCodeResponse response =
                new RecoveryCodeResponse(recoveryCodeService.regenerate(request.currentPassword()));
        auditService.record(
                AuditEventType.RECOVERY_CODE_REGENERATED,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return response;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ProblemDetails.of(
                            HttpStatus.UNAUTHORIZED,
                            "Unauthorized",
                            "Authentication required",
                            "UNAUTHORIZED",
                            ErrorReason.UNKNOWN));
        }

        Nexus nexus = nexusService.getNexus();
        return ResponseEntity.ok(new NexusMeResponse(
                nexus.getNexusId(),
                nexus.getNexusName(),
                nexus.getIpAddress(),
                nexus.getPort(),
                nexus.getUsername(),
                nexusService.hasRecoveryCode()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok().build();
    }

    /**
     * Revokes all admin JWTs by bumping token_version.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(HttpServletRequest http) {
        nexusAuthService.logoutAll();
        auditService.record(
                AuditEventType.LOGOUT_ALL,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-credentials")
    public ResponseEntity<?> changeCredentials(
            @RequestBody ChangeCredentialsRequest request, HttpServletRequest http) {
        nexusAuthService.changeCredentials(
                request.currentPassword(),
                request.newUsername(),
                request.newPassword()
        );
        auditService.record(
                AuditEventType.CREDENTIALS_CHANGED,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return ResponseEntity.ok().build();
    }
}
