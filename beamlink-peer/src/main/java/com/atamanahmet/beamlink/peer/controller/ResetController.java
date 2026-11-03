package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.dto.DeviceResetResponse;
import com.atamanahmet.beamlink.peer.service.ResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/device/reset")
@RequiredArgsConstructor
public class ResetController {

    private final ResetService resetService;

    /**
     * Clears local identity and registers again with nexus.
     */
    @PostMapping("/re-register")
    public ResponseEntity<DeviceResetResponse> reRegister(
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(resetService.reRegister(force));
    }

    /**
     * Wipes device data rows and temp files, then reloads a fresh identity.
     */
    @PostMapping("/factory")
    public ResponseEntity<Map<String, String>> factoryReset(
            @RequestParam(defaultValue = "false") boolean force) {
        resetService.factoryReset(force);
        return ResponseEntity.ok(Map.of("message", "Factory reset complete."));
    }
}
