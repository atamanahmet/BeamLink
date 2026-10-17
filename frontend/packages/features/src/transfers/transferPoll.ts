import type { TransferSummary } from "@beamlink/core";

/**
 * True when any transfer is still in flight.
 */
export function hasActive(transfers: TransferSummary[]): boolean {
  return transfers.some(
    (t) => t.status === "ACTIVE" || t.status === "PENDING",
  );
}

/**
 * Poll fast while active, slow when idle.
 */
export function getTransferPollInterval(
  transfers: TransferSummary[] | undefined,
): number {
  if (transfers && hasActive(transfers)) return 500;
  return 5000;
}
