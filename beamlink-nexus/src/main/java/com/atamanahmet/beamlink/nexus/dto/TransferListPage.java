package com.atamanahmet.beamlink.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferListPage {

    private List<TransferSummary> items;
    private long total;
    private boolean hasMore;
}
