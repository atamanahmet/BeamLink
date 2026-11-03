package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.exception.InvalidRequestException;
import com.atamanahmet.beamlink.peer.service.StorageService;
import com.atamanahmet.beamlink.peer.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/device/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final SystemService systemService;

    @PostMapping("/open-uploads-folder")
    public ResponseEntity<Void> openUploadsFolder() {
        systemService.openWithOs(storageService.getUploadsDir());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/open-logs")
    public ResponseEntity<Void> openLogs() {
        systemService.openWithOs(storageService.getLogsPath());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/upload-dir")
    public ResponseEntity<Map<String, String>> getUploadDir() {
        return ResponseEntity.ok(
                Map.of("path", storageService.getUploadsDir().toString().replace("\\", "/"))
        );
    }

    @PutMapping("/upload-dir")
    public ResponseEntity<Map<String, String>> updateUploadDir(@RequestBody Map<String, String> body) {
        String rawPath = body.get("path");

        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidRequestException("Path must not be empty");
        }

        Path newPath;
        try {
            newPath = Path.of(rawPath);
        } catch (InvalidPathException e) {
            throw new InvalidRequestException("Invalid path: " + e.getMessage(), e);
        }

        storageService.updateUploadsDir(newPath);

        return ResponseEntity.ok(
                Map.of("path", storageService.getUploadsDir().toString().replace("\\", "/"))
        );
    }
}