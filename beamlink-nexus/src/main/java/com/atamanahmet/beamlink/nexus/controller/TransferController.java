package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.service.TransferSenderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/nexus/admin/transfers")
@RequiredArgsConstructor
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferSenderService senderService;

    /**
     * User initiates a transfer from the UI.
     * Returns transferId immediately, sending happens async in background.
     */
    @PostMapping
    public ResponseEntity<InitiateTransferResponse> initiate(
            @RequestBody InitiateTransferRequest request) {

        InitiateTransferResponse response = senderService.initiate(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * UI polls this every second to show progress.
     */
    @GetMapping("/{transferId}/status")
    public ResponseEntity<TransferStatusResponse> getStatus(
            @PathVariable UUID transferId) {

        FileTransfer transfer = senderService.getTransfer(transferId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(TransferMapper.toResponse(transfer));
    }

    @GetMapping
    public ResponseEntity<List<TransferStatusResponse>> getAll() {
        List<TransferStatusResponse> transfers = senderService
                .getAll()
                .stream()
                .map(TransferMapper::toResponse)
                .toList();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(transfers);
    }

    /**
     * Resume a paused transfer.
     * Queries target for confirmed offset first, then restarts async sending.
     * Returns 409 if transfer is not paused.
     * Returns 503 if target is unreachable, UI keeps showing PAUSED with disabled resume button.
     */
    @PostMapping("/{transferId}/resume")
    public ResponseEntity<Void> resume(@PathVariable UUID transferId) {
        try {
            senderService.resume(transferId);
            return ResponseEntity.ok().build();
        } catch (FileTransferException e) {
            // Target offline or wrong state
            String msg = e.getMessage() != null ? e.getMessage() : "Resume failed";
            if (msg.contains("Cannot reach target")) {
                return ResponseEntity.status(503).build();
            }
            if (msg.contains("not paused")) {
                return ResponseEntity.status(409).build();
            }
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Cancel an in progress transfer.
     * Sender loop checks status before each chunk and stops on CANCELLED.
     */
    @DeleteMapping("/{transferId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID transferId) {

        senderService.cancel(transferId);

        return ResponseEntity
                .noContent()
                .build();
    }

//    /**
//     * Delete existing transfer
//     */
//    @DeleteMapping("/{transferId}/delete")
//    public ResponseEntity<Void> delete(@PathVariable UUID transferId) {
//        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
//            TransferStatus s = transfer.getStatus();
//            if (s == TransferStatus.COMPLETED
//                    || s == TransferStatus.FAILED
//                    || s == TransferStatus.CANCELLED
//                    || s == TransferStatus.EXPIRED) {
//                transferRepository.delete(transfer);
//                log.info("Transfer deleted by user: {}", transferId);
//            }
//        });
//        return ResponseEntity.noContent().build();
//    }

}