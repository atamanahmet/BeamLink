package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.security.DeviceKey;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.service.PeerCacheService;
import com.atamanahmet.beamlink.peer.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides device status information
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class StatusController {

    private final DeviceConfig config;
    private final DeviceService deviceService;
    private final NexusAddressHolder nexusAddressHolder;
    private final StorageService storageService;
    private final PeerCacheService peerCacheService;
    private final DeviceKey deviceKey;
    private final NexusTlsPin nexusTlsPin;
    /**
     * Get device status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {

        Map<String, Object> status = new HashMap<>();

        status.put("deviceId", deviceService.getDeviceId());
        status.put("name", deviceService.getDeviceName());
        status.put("port", config.getDevicePort());
        status.put("uploadDirectory", storageService.getUploadsDir());
        status.put("fileCount", storageService.getFileCount());
        status.put("status", "online");
        status.put("nexusUrl", nexusAddressHolder.getNexusUrl());
        status.put("state", deviceService.getState() != null ? deviceService.getState().name() : "");
        status.put("pinSet", nexusTlsPin.hasPin());
        String hint = peerCacheService.getLastNexusAuthHint();
        status.put("hint", hint != null ? hint : "");
        status.put("fingerprint", deviceKey.fingerprint());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(status);
    }
}