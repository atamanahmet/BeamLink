package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.dto.PeerSummaryResponse;
import com.atamanahmet.beamlink.peer.mapper.PeerMapper;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.service.PeerCacheService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Provides peer list for UI
 */
@RestController
@RequestMapping("/api/device/peers")
@RequiredArgsConstructor
public class PeerController {

    private final PeerCacheService peerCacheService;
    private final DeviceService deviceService;

    /**
     * Get list of all peers
     */
    @GetMapping
    public ResponseEntity<List<PeerSummaryResponse>> getAllPeers() {
        List<PeerSummaryResponse> peers = peerCacheService.getAllPeers(
                deviceService.getDeviceId(),
                deviceService.getPublicToken()
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
                deviceService.getDeviceId(),
                deviceService.getPublicToken()
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
                deviceService.getDeviceId(),
                deviceService.getPublicToken()
        );
        List<PeerSummaryResponse> peers = peerCacheService.getAllPeers(
                deviceService.getDeviceId(),
                deviceService.getPublicToken()
        ).stream().map(PeerMapper::toSummary).toList();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }
}
