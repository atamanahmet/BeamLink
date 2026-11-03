package com.atamanahmet.beamlink.peer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class InitiateDirectoryTransferResponse {
    private UUID directoryTransferId;
    private int skippedCount;
}