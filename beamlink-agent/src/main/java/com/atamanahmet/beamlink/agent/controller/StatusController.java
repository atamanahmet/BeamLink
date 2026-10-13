package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.config.NexusAddressHolder;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.atamanahmet.beamlink.agent.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides agent status information
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class StatusController {

    private final AgentConfig config;
    private final AgentService agentService;
    private final NexusAddressHolder nexusAddressHolder;
    private final StorageService storageService;
    /**
     * Get agent status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {

        Map<String, Object> status = new HashMap<>();

        status.put("agentId", agentService.getAgentId());
        status.put("name", agentService.getAgentName());
        status.put("port", config.getAgentPort());
        status.put("uploadDirectory", storageService.getUploadsDir());
        status.put("fileCount", storageService.getFileCount());
        status.put("status", "online");
        status.put("nexusUrl", nexusAddressHolder.getNexusUrl());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(status);
    }
}