package com.atamanahmet.beamlink.peer.dto;

import java.util.List;

/**
 * Dispatch result plus how many source entries were skipped.
 */
public record InitiateSendResponse(List<DispatchResultItem> items, int skippedCount) {
}
