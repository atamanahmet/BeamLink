package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.*;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.service.ChunkReceiverService;
import com.atamanahmet.beamlink.agent.service.TransferSenderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferInboundController {

    private static final Logger log = LoggerFactory.getLogger(TransferInboundController.class);

    private final ChunkReceiverService receiverService;

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

        long offset = parseOffset(contentRange);
        ChunkAckResponse ack = receiverService.receiveChunk(
                transferId, offset, request.getInputStream());

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
     * Parses byte offset from Content-Range header
     */
    private long parseOffset(String contentRange) {
        try {
            String bytesPart = contentRange.replace("bytes ", "");
            String offsetStr = bytesPart.substring(0, bytesPart.indexOf('-'));
            return Long.parseLong(offsetStr.trim());
        } catch (Exception e) {
            throw new FileTransferException("Invalid Content-Range header: " + contentRange, e);
        }
    }
}