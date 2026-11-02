package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import com.atamanahmet.beamlink.nexus.dto.ResetResponse;
import com.atamanahmet.beamlink.nexus.service.AuditService;
import com.atamanahmet.beamlink.nexus.service.ResetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nexus/admin/reset")
@RequiredArgsConstructor
public class ResetController {

    private final ResetService resetService;
    private final AuditService auditService;

    /**
     * Deletes devices, transfers and logs. Keeps the admin account and JWT keys.
     */
    @PostMapping("/network")
    public ResponseEntity<ResetResponse> resetNetworkData(
            @RequestParam(defaultValue = "false") boolean force) {
        resetService.resetNetworkData(force);
        return ResponseEntity.ok(new ResetResponse("Network data reset complete."));
    }

    /**
     * Same as network reset, plus the admin account so first-run setup shows next.
     */
    @PostMapping("/factory")
    public ResponseEntity<ResetResponse> factoryReset(
            @RequestParam(defaultValue = "false") boolean force,
            HttpServletRequest http) {
        resetService.factoryReset(force);
        auditService.record(
                AuditEventType.FACTORY_RESET,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                null,
                null);
        return ResponseEntity.ok(new ResetResponse("Factory reset complete."));
    }
}
