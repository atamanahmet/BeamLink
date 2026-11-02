package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.dto.DeviceRenameRequest;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/device")
@RequiredArgsConstructor
public class DeviceEndpointController {

    private final DeviceService deviceService;

    @PostMapping("/{id}/rename")
    public ResponseEntity<Void> requestRename(
            @PathVariable UUID id,
            @RequestBody DeviceRenameRequest request) {
        deviceService.requestRename(id, request.getName());
        return ResponseEntity.ok().build();
    }
}