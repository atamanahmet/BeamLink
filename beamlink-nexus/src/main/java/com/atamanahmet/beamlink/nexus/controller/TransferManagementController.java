package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.dto.DispatchResultItem;
import com.atamanahmet.beamlink.nexus.dto.InitiateSendRequest;
import com.atamanahmet.beamlink.nexus.dto.TransferSummary;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.service.TransferDispatchService;
import com.atamanahmet.beamlink.nexus.service.TransferManagementService;
import com.atamanahmet.beamlink.nexus.service.TransferQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/admin/transfers")
@RequiredArgsConstructor
public class TransferManagementController {

    private final TransferDispatchService dispatchService;
    private final TransferQueryService queryService;
    private final TransferManagementService managementService;

    @PostMapping("/send")
    public ResponseEntity<List<DispatchResultItem>> send(
            @RequestBody InitiateSendRequest request) {
        return ResponseEntity.ok(dispatchService.dispatch(request));
    }

    @GetMapping
    public ResponseEntity<List<TransferSummary>> listAll() {
        return ResponseEntity.ok(queryService.listAll());
    }

    @GetMapping("/single/{id}")
    public ResponseEntity<TransferSummary> getSingle(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getSingle(id));
    }

    @GetMapping("/batch/{id}")
    public ResponseEntity<TransferSummary> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getBatch(id));
    }

    @GetMapping("/directory/{id}")
    public ResponseEntity<TransferSummary> getDirectory(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getDirectory(id));
    }

    @GetMapping("/batch/{id}/files")
    public ResponseEntity<List<TransferSummary>> getBatchFiles(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getBatchFiles(id));
    }

    @GetMapping("/directory/{id}/files")
    public ResponseEntity<List<TransferSummary>> getDirectoryFiles(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getDirectoryFiles(id));
    }

    /**
     * SINGLE management
     */
    @PostMapping("/single/{id}/pause")
    public ResponseEntity<Void> pauseSingle(@PathVariable UUID id) {
        managementService.pauseSingle(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/single/{id}/resume")
    public ResponseEntity<Void> resumeSingle(@PathVariable UUID id) {
        return handleResume(() -> managementService.resumeSingle(id));
    }

    @DeleteMapping("/single/{id}")
    public ResponseEntity<Void> cancelSingle(@PathVariable UUID id) {
        managementService.cancelSingle(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/single/{id}/delete")
    public ResponseEntity<Void> deleteSingle(@PathVariable UUID id) {
        managementService.deleteSingle(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * BATCH management
     */
    @PostMapping("/batch/{id}/pause")
    public ResponseEntity<Void> pauseBatch(@PathVariable UUID id) {
        managementService.pauseBatch(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/{id}/resume")
    public ResponseEntity<Void> resumeBatch(@PathVariable UUID id) {
        return handleResume(() -> managementService.resumeBatch(id));
    }

    @DeleteMapping("/batch/{id}")
    public ResponseEntity<Void> cancelBatch(@PathVariable UUID id) {
        managementService.cancelBatch(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/batch/{id}/delete")
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID id) {
        managementService.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DIRECTORY management
     */
    @PostMapping("/directory/{id}/pause")
    public ResponseEntity<Void> pauseDirectory(@PathVariable UUID id) {
        managementService.pauseDirectory(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/directory/{id}/resume")
    public ResponseEntity<Void> resumeDirectory(@PathVariable UUID id) {
        return handleResume(() -> managementService.resumeDirectory(id));
    }

    @DeleteMapping("/directory/{id}")
    public ResponseEntity<Void> cancelDirectory(@PathVariable UUID id) {
        managementService.cancelDirectory(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/directory/{id}/delete")
    public ResponseEntity<Void> deleteDirectory(@PathVariable UUID id) {
        managementService.deleteDirectory(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resume can fail if target is unreachable, handle consistently across all types.
     */
    private ResponseEntity<Void> handleResume(Runnable action) {
        try {
            action.run();
            return ResponseEntity.ok().build();
        } catch (FileTransferException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Cannot reach target")) return ResponseEntity.status(503).build();
            if (msg.contains("not paused"))          return ResponseEntity.status(409).build();
            return ResponseEntity.badRequest().build();
        }
    }
}