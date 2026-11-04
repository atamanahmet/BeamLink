import type { TransferSummary } from "@beamlink/core";

const FINISHED = new Set(["COMPLETED", "FAILED", "CANCELLED"]);
const FINISHED_CAP = 20;

/**
 * True when the job is still in flight.
 */
function isActiveJob(status: string): boolean {
  return status === "ACTIVE" || status === "PAUSED" || status === "PENDING";
}

/**
 * Maps a native send/receive progress state onto TransferSummary.status.
 */
export function toTransferStatus(state: string): string {
  if (state === "sending" || state === "receiving") {
    return "ACTIVE";
  }
  if (state === "paused") {
    return "PAUSED";
  }
  if (state === "completed") {
    return "COMPLETED";
  }
  if (state === "cancelled") {
    return "CANCELLED";
  }
  return "FAILED";
}

/**
 * Badge color for a phone transfer status.
 */
export function statusBadgeColor(status: string): string {
  if (status === "COMPLETED") {
    return "success";
  }
  if (status === "FAILED") {
    return "danger";
  }
  if (status === "ACTIVE") {
    return "brand";
  }
  return "gray";
}

/**
 * Short status word for a phone transfer card.
 */
export function statusWord(
  status: string,
  direction: "send" | "receive",
): string {
  if (status === "ACTIVE") {
    return direction === "receive" ? "Receiving" : "Sending";
  }
  if (status === "PAUSED") {
    return "Paused";
  }
  if (status === "COMPLETED") {
    return "Done";
  }
  if (status === "CANCELLED") {
    return "Cancelled";
  }
  return "Failed";
}

/**
 * Active jobs first, then newest finished, failed, and cancelled. Cap 20.
 */
export function orderPhoneTransfers(
  transfers: TransferSummary[],
): TransferSummary[] {
  const active = transfers.filter((t) => isActiveJob(t.status));
  const finished = transfers
    .filter((t) => FINISHED.has(t.status))
    .sort((a, b) => {
      const at = a.completedAt ? Date.parse(a.completedAt) : 0;
      const bt = b.completedAt ? Date.parse(b.completedAt) : 0;
      return bt - at;
    })
    .slice(0, FINISHED_CAP);
  return [...active, ...finished];
}
