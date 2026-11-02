package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.dto.AuditLogResponse;
import com.atamanahmet.beamlink.nexus.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nexus/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private static final int MAX_SIZE = 200;

    private final AuditService auditService;

    /**
     * Paged audit rows, newest first.
     */
    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int pageSize = Math.min(size, MAX_SIZE);
        return ResponseEntity.ok(auditService.list(page, pageSize));
    }
}
