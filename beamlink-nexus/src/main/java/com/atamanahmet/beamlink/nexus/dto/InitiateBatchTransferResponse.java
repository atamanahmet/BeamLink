package com.atamanahmet.beamlink.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class InitiateBatchTransferResponse {
    private UUID batchTransferId;
}