package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.*;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.service.ChunkReceiverService;
import com.atamanahmet.beamlink.agent.service.TransferManagementService;
import com.atamanahmet.beamlink.agent.service.TransferSenderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferInboundController {

    private final ChunkReceiverService receiverService;
    private final TransferSenderService senderService;
    private final TransferManagementService managementService;

    /**
     * Source registers the transfer before sending any chunks, target prepares partial file on disk
     */
    @PostMapping("/receive")
    public ResponseEntity<Void> prepareReceive(@RequestBody PrepareReceiveRequest request) {

        FileTransfer transfer = FileTransfer.initiate(
                request.getTransferId(),
                request.getSourceAgentId(),
                null,
                request.getFileName(),
                null,
                request.getFileSize()
        );
        transfer.setSourceIp(request.getSourceIp());
        transfer.setSourcePort(request.getSourcePort());
        transfer.setStatus(TransferStatus.ACTIVE);

        receiverService.prepareReceive(transfer);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /**
     * Target receives directory registration, creates records and allocates all partial files
     */
    @PostMapping("/receive-directory")
    public ResponseEntity<Void> prepareReceiveDirectory(
            @RequestBody ReceiveDirectoryRequest request) {

        receiverService.prepareReceiveDirectory(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /**
     * Target receives batch registration, creates records and allocates all partial files
     */
    @PostMapping("/receive-batch")
    public ResponseEntity<Void> prepareReceiveBatch(
            @RequestBody ReceiveBatchRequest request) {

        receiverService.prepareReceiveBatch(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /**
     * Receives a raw chunk and writes it to disk at the correct offset
     */
    @PatchMapping("/{transferId}/chunk")
    public ResponseEntity<ChunkAckResponse> receiveChunk(
            @PathVariable UUID transferId,
            @RequestHeader("Content-Range") String contentRange,
            HttpServletRequest request) throws IOException {

        long[] range = parseContentRange(contentRange);
        ChunkAckResponse ack = receiverService.receiveChunk(
                transferId, range[0], range[1], request.getInputStream());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ack);
    }

    /**
     * Source queries confirmed offset before resuming a paused transfer
     */
    @GetMapping("/{transferId}/offset")
    public ResponseEntity<Map<String, Long>> getOffset(@PathVariable UUID transferId) {
        long confirmedOffset = receiverService.getConfirmedOffset(transferId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Map.of("confirmedOffset", confirmedOffset));
    }

    /**
     * Parses offset and length from Content-Range bytes start-end/total.
     */
    private long[] parseContentRange(String contentRange) {
        try {
            String bytesPart = contentRange.replace("bytes ", "");
            int dash = bytesPart.indexOf('-');
            int slash = bytesPart.indexOf('/');
            long start = Long.parseLong(bytesPart.substring(0, dash).trim());
            long end = Long.parseLong(bytesPart.substring(dash + 1, slash).trim());
            long length = end - start + 1L;
            if (length < 0L) {
                throw new FileTransferException("Invalid Content-Range length: " + contentRange, null);
            }
            return new long[] { start, length };
        } catch (FileTransferException e) {
            throw e;
        } catch (Exception e) {
            throw new FileTransferException("Invalid Content-Range header: " + contentRange, e);
        }
    }

    /**
     * Peer notifies us to cancel our side of the transfer
     */
    @DeleteMapping("/{transferId}/cancel-notify")
    public ResponseEntity<Void> cancelNotify(@PathVariable UUID transferId) {
        receiverService.cancelReceive(transferId);
        senderService.cancel(transferId);
        return ResponseEntity.ok().build();
    }

    /**
     * Peer notifies us to pause our side of the transfer
     */
    @PostMapping("/{transferId}/pause-notify")
    public ResponseEntity<Void> pauseNotify(
            @PathVariable UUID transferId,
            @RequestBody PauseNotifyRequest request) {
        managementService.applyPauseNotify(transferId, request.getPausedBy());
        return ResponseEntity.ok().build();
    }

    /**
     * Peer notifies us to resume our side of the transfer
     */
    @PostMapping("/{transferId}/resume-notify")
    public ResponseEntity<Void> resumeNotify(@PathVariable UUID transferId) {
        managementService.applyResumeNotify(transferId);
        return ResponseEntity.ok().build();
    }
}