package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/admin/devices")
@RequiredArgsConstructor
public class NexusDeviceController {

    private final DeviceService deviceService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<List<Device>> getAll() {

        List<Device> allDevices = deviceService.getAllDevices();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allDevices);
    }

    @GetMapping("/approved")
    public ResponseEntity<List<Device>> getAllApproved() {

        List<Device> allApproved = deviceService.getAllApproved();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allApproved);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Device>> getPending() {
        List<Device> allPending = deviceService.getAllPending();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allPending);
    }

    @GetMapping("/rename-pending")
    public ResponseEntity<List<Device>> getRenamePendingAll() {

        List<Device> renamePendingAll = deviceService.getPendingRenames();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(renamePendingAll);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<HttpStatus> approve(@PathVariable UUID id, HttpServletRequest http) {

        deviceService.approveDevice(id);
        Device device = deviceService.findByDeviceId(id);
        auditService.record(
                AuditEventType.DEVICE_APPROVED,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                device.getId().toString(),
                device.getName());

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<HttpStatus> reject(@PathVariable UUID id, HttpServletRequest http) {

        deviceService.rejectDevice(id);
        Device device = deviceService.findByDeviceId(id);
        auditService.record(
                AuditEventType.DEVICE_REJECTED,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                device.getId().toString(),
                device.getName());

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/{id}/rename/approve")
    public ResponseEntity<HttpStatus> approveRename(@PathVariable UUID id, HttpServletRequest http) {

        Device device = deviceService.findByDeviceId(id);
        String oldName = device.getName();
        String newName = device.getRequestedName();
        deviceService.approveRename(id);
        auditService.record(
                AuditEventType.DEVICE_RENAMED,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                device.getId().toString(),
                oldName + " -> " + newName);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/{id}/rename/reject")
    public ResponseEntity<HttpStatus> rejectRename(@PathVariable UUID id) {

        deviceService.rejectRename(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> delete(@PathVariable UUID id, HttpServletRequest http) {

        Device device = deviceService.findByDeviceId(id);
        deviceService.deleteDevice(id);
        auditService.record(
                AuditEventType.DEVICE_REMOVED,
                AuditOutcome.SUCCESS,
                http.getRemoteAddr(),
                device.getId().toString(),
                device.getName());

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
    
    
}