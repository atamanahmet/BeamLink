package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.service.NexusFirstContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device/nexus")
@RequiredArgsConstructor
public class NexusSetupController {

    private final NexusFirstContactService firstContact;

    @GetMapping("/status")
    public ResponseEntity<NexusFirstContactService.Status> status() {
        return ResponseEntity.ok(firstContact.status());
    }

    @PostMapping("/probe")
    public ResponseEntity<NexusFirstContactService.ProbeResult> probe(
            @RequestBody NexusAddressRequest request) {
        return ResponseEntity.ok(firstContact.probe(request.ip(), request.port()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody NexusConfirmRequest request) {
        firstContact.confirm(request.ip(), request.port(), request.fingerprint());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/pin")
    public ResponseEntity<Void> clearPin() {
        firstContact.clearPin();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/address")
    public ResponseEntity<Void> clearAddress() {
        firstContact.clearAddressAndPin();
        return ResponseEntity.ok().build();
    }

    public record NexusAddressRequest(String ip, int port) {
    }

    public record NexusConfirmRequest(String ip, int port, String fingerprint) {
    }
}
