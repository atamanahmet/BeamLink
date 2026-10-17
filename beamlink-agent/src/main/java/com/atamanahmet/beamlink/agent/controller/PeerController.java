package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.dto.PeerSummaryResponse;
import com.atamanahmet.beamlink.agent.mapper.PeerMapper;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.atamanahmet.beamlink.agent.service.PeerCacheService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Provides peer list for UI
 */
@RestController
@RequestMapping("/api/agent/peers")
@RequiredArgsConstructor
public class PeerController {

    private final PeerCacheService peerCacheService;
    private final AgentService agentService;

    /**
     * Get list of all peers
     */
    @GetMapping
    public ResponseEntity<List<PeerSummaryResponse>> getAllPeers() {
        List<PeerSummaryResponse> peers = peerCacheService.getAllPeers(
                agentService.getAgentId(),
                agentService.getPublicToken()
        ).stream().map(PeerMapper::toSummary).toList();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }

    /**
     * Get list of available peers
     */
    @GetMapping("/online")
    public ResponseEntity<List<PeerSummaryResponse>> getPeers() {
        List<PeerSummaryResponse> peers = peerCacheService.getOnlinePeers(
                agentService.getAgentId(),
                agentService.getPublicToken()
        ).stream().map(PeerMapper::toSummary).toList();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }

    /**
     * Refresh peer list from nexus
     */
    @PostMapping("/refresh")
    public ResponseEntity<List<PeerSummaryResponse>> refreshPeers() {
        peerCacheService.refreshPeersFromNexus(
                agentService.getAgentId(),
                agentService.getPublicToken()
        );
        List<PeerSummaryResponse> peers = peerCacheService.getAllPeers(
                agentService.getAgentId(),
                agentService.getPublicToken()
        ).stream().map(PeerMapper::toSummary).toList();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }
}
