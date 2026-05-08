package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.dto.TransferStatusResponse;

/**
 * Shared mapper with both nexus and agents to map transfer response
 */
public class TransferMapper {

    private TransferMapper() {}

    public static TransferStatusResponse toResponse(FileTransfer t) {
        return new TransferStatusResponse(
                t.getTransferId(),
                t.getStatus(),
                t.getConfirmedOffset(),
                t.getFileSize(),
                t.getFileName(),
                t.getFailureReason(),
                t.getTargetAgentId(),
                t.getCreatedAt(),
                t.getLastChunkAt()
        );
    }
}