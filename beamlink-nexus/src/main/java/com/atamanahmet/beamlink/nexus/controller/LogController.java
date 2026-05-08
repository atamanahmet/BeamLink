package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.TransferLog;

import com.atamanahmet.beamlink.nexus.service.TransferLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/nexus/admin/logs")
@RequiredArgsConstructor
public class LogController {

    private final TransferLogService transferLogService;

    /**
     * All logs with page
     */
    @GetMapping
    public ResponseEntity<List<TransferLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(transferLogService.getLogs(PageRequest.of(page, size)));
    }

    /**
     * Recent logs, with default 50
     */
    @GetMapping("/recent")
    public ResponseEntity<List<TransferLog>> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(transferLogService.getRecentLogs(limit));
    }
}