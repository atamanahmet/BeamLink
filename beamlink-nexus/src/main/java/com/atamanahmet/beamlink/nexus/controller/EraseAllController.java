package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import com.atamanahmet.beamlink.nexus.dto.ResetResponse;
import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetails;
import com.atamanahmet.beamlink.nexus.security.EraseAllCallerRule;
import com.atamanahmet.beamlink.nexus.service.AuditService;
import com.atamanahmet.beamlink.nexus.service.ResetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nexus/auth")
@RequiredArgsConstructor
public class EraseAllController {

    public static final String HEADER = "X-UI-Secret";

    private final ResetService resetService;
    private final AuditService auditService;

    @Value("${NEXUS_UI_SECRET:}")
    private String uiSecret;

    /**
     * Wipes network data and the admin account. Loopback plus UI secret only.
     */
    @PostMapping("/erase")
    public ResponseEntity<?> eraseAll(HttpServletRequest http) {
        if (!EraseAllCallerRule.allowed(
                http.getRemoteAddr(), http.getHeader(HEADER), uiSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ProblemDetails.of(
                            HttpStatus.FORBIDDEN,
                            "Forbidden",
                            "Forbidden",
                            "FORBIDDEN",
                            ErrorReason.UNKNOWN));
        }
        resetService.factoryReset(true);
        auditService.record(
                AuditEventType.ERASE_ALL,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return ResponseEntity.ok(new ResetResponse("Factory reset complete."));
    }
}
