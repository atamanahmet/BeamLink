package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.config.NexusAddressHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/nexus")
@RequiredArgsConstructor
public class NexusSetupController {

    private final NexusAddressHolder nexusAddressHolder;

    @PostMapping("/address")
    public ResponseEntity<Void> setAddress(@RequestBody NexusAddressRequest request) {
        String url = "http://" + request.ip().trim() + ":" + request.port();
        nexusAddressHolder.setNexusUrl(url);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/address")
    public ResponseEntity<Void> clearAddress() {
        nexusAddressHolder.clear();
        return ResponseEntity.ok().build();
    }

    public record NexusAddressRequest(String ip, int port) {}
}