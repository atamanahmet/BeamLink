package com.atamanahmet.beamlink.peer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class InitiateBatchTransferResponse {
    private UUID batchTransferId;
    private int skippedCount;
}