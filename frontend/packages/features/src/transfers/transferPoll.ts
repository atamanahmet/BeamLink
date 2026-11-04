import type { TransferSummary } from "@beamlink/core";

/** PENDING older than this is stalled. */
export const PENDING_STALL_MS = 30_000;

/**
 * True when a PENDING transfer is at least 30s old.
 */
export function isStalledPending(
  status: string,
  createdAt: string,
  nowMs: number,
): boolean {
  if (status !== "PENDING") return false;
  return nowMs - new Date(createdAt).getTime() >= PENDING_STALL_MS;
}

/**
 * True when any transfer is still in flight.
 * Stalled PENDING does not count.
 */
export function hasActive(
  transfers: TransferSummary[],
  nowMs: number = Date.now(),
): boolean {
  return transfers.some(
    (t) =>
      t.status === "ACTIVE" ||
      (t.status === "PENDING" &&
        !isStalledPending(t.status, t.createdAt, nowMs)),
  );
}

/**
 * Poll fast while active, 2s when only stalled PENDING, slow when idle.
 */
export function getTransferPollInterval(
  transfers: TransferSummary[] | undefined,
  nowMs: number = Date.now(),
): number {
  if (!transfers) return 5000;
  if (hasActive(transfers, nowMs)) return 500;
  if (transfers.some((t) => isStalledPending(t.status, t.createdAt, nowMs))) {
    return 2000;
  }
  return 5000;
}
