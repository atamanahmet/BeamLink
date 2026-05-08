package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/nexus/agent/logs")
@RequiredArgsConstructor
public class AgentLogController {

    private final TransferLogService transferLogService;

    /**
     * Agents push their local logs to Nexus for central record
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncLogs(
            Authentication authentication,
            @RequestBody List<LogSyncRequest> incomingLogs) {

        if (incomingLogs == null || incomingLogs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No logs provided"));
        }

        UUID agentId = (UUID) authentication.getPrincipal();
        List<UUID> mergedIds = transferLogService.sync(agentId, incomingLogs);

        log.info("Synced {} logs from agent {}", mergedIds.size(), agentId);

        return ResponseEntity.ok(Map.of("success", true, "mergedLogIds", mergedIds));
    }
}