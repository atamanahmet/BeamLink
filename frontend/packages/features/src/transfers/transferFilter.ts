import type { TransferSummary } from "@beamlink/core";

export const TRANSFER_STATE_FILTERS = [
  "ALL",
  "ACTIVE",
  "PAUSED",
  "PENDING",
  "COMPLETED",
  "FAILED",
] as const;

export type TransferStateFilter = (typeof TRANSFER_STATE_FILTERS)[number];

/**
 * Filters transfers by a top-bar state chip.
 */
export function filterTransfers(
  transfers: TransferSummary[],
  filter: TransferStateFilter,
): TransferSummary[] {
  if (filter === "ALL") return transfers;
  return transfers.filter((t) => t.status === filter);
}

/**
 * Counts transfers per top-bar state chip.
 */
export function countByState(
  transfers: TransferSummary[],
): Record<TransferStateFilter, number> {
  const counts: Record<TransferStateFilter, number> = {
    ALL: transfers.length,
    ACTIVE: 0,
    PAUSED: 0,
    PENDING: 0,
    COMPLETED: 0,
    FAILED: 0,
  };
  for (const t of transfers) {
    if (t.status === "ACTIVE") counts.ACTIVE += 1;
    else if (t.status === "PAUSED") counts.PAUSED += 1;
    else if (t.status === "PENDING") counts.PENDING += 1;
    else if (t.status === "COMPLETED") counts.COMPLETED += 1;
    else if (t.status === "FAILED") counts.FAILED += 1;
  }
  return counts;
}
