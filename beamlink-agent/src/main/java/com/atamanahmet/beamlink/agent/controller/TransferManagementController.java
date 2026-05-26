package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.dto.DispatchResultItem;
import com.atamanahmet.beamlink.agent.dto.InitiateSendRequest;
import com.atamanahmet.beamlink.agent.dto.TransferSummary;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.service.TransferDispatchService;
import com.atamanahmet.beamlink.agent.service.TransferManagementService;
import com.atamanahmet.beamlink.agent.service.TransferQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/transfers")
@RequiredArgsConstructor
public class TransferManagementController {


    private final TransferDispatchService dispatchService;
    private final TransferQueryService queryService;
    private final TransferManagementService managementService;

    @GetMapping
    public ResponseEntity<List<TransferSummary>> listAll() {
        return ResponseEntity.ok(queryService.listAll());
    }

    @PostMapping("/dispatch")
    public ResponseEntity<List<DispatchResultItem>> dispatch(
            @RequestBody InitiateSendRequest request) {
        return ResponseEntity.ok(dispatchService.dispatch(request));
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