package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.dto.DeviceRenameRequest;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device")
public class DeviceManagementController {

    private final DeviceService deviceService;

    @PostMapping("/{id}/rename")
    public ResponseEntity<HttpStatus> requestRename(
            @PathVariable UUID id,
            @RequestBody DeviceRenameRequest request) {
        deviceService.requestRename(id, request.getName());
        return ResponseEntity
                .ok()
                .build();
    }
}