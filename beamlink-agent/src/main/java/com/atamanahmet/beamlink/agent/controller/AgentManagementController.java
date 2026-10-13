package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.dto.AgentRenameRequest;
import com.atamanahmet.beamlink.agent.dto.AgentRenameResponse;
import com.atamanahmet.beamlink.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
public class AgentManagementController {

    private final AgentService agentService;

    @PostMapping("/{id}/rename")
    public ResponseEntity<HttpStatus> requestRename(
            @PathVariable UUID id,
            @RequestBody AgentRenameRequest request) {
        agentService.requestRename(id, request.getName());
        return ResponseEntity
                .ok()
                .build();
    }

    @PostMapping("/rename")
    public ResponseEntity<HttpStatus> receiveRename(@RequestBody AgentRenameResponse payload) {
        if (payload.getAgentName() != null && !payload.getAgentName().isBlank()) {
            agentService.updateAgentName(payload.getAgentName());
        }
        return ResponseEntity
                .ok()
                .build();
    }
}