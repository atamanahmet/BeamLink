import type { TransferSummary } from "@beamlink/core";
import { formatMbps, type SpeedUnit } from "./speedUnits.ts";

export type DispatchGroup = {
  dispatchId: string;
  transfers: TransferSummary[];
  totalSize: number;
  confirmedBytes: number;
  status: string;
  createdAt: string;
  targetAgentId: string;
};

/**
 * Prefers BATCH or DIRECTORY DTO status for a dispatch card.
 */
function dispatchGroupStatus(members: TransferSummary[]): string {
  const groupRow = members.find(
    (m) => m.type === "BATCH" || m.type === "DIRECTORY",
  );
  if (groupRow?.status) {
    return groupRow.status;
  }
  return members[0]?.status ?? "PENDING";
}

/**
 * Splits transfers into dispatch groups and standalone rows.
 */
export function groupTransfers(transfers: TransferSummary[]): {
  groups: DispatchGroup[];
  standalones: TransferSummary[];
} {
  const groupMap = new Map<string, TransferSummary[]>();
  const standalones: TransferSummary[] = [];

  for (const t of transfers) {
    if (
      t.dispatchId &&
      transfers.filter((x) => x.dispatchId === t.dispatchId).length > 1
    ) {
      const existing = groupMap.get(t.dispatchId) ?? [];
      groupMap.set(t.dispatchId, [...existing, t]);
    } else {
      standalones.push(t);
    }
  }

  const groups: DispatchGroup[] = Array.from(groupMap.entries()).map(
    ([dispatchId, members]) => ({
      dispatchId,
      transfers: members,
      totalSize: members.reduce((sum, t) => sum + t.totalSize, 0),
      confirmedBytes: members.reduce((sum, t) => sum + t.confirmedBytes, 0),
      status: dispatchGroupStatus(members),
      createdAt: members[0].createdAt,
      targetAgentId: members[0].targetAgentId,
    }),
  );

  groups.sort(
    (a, b) =>
      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );

  return { groups, standalones };
}

/**
 * Formats an elapsed duration for display.
 */
export function formatElapsedMs(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

/**
 * Speed label for an active transfer in the chosen unit.
 */
export function formatSpeed(t: TransferSummary, unit: SpeedUnit): string {
  if (t.status !== "ACTIVE" || t.confirmedBytes === 0) return "";
  const elapsedSec = t.activeTransferMs / 1000;
  if (elapsedSec < 1) return "";
  const mbps = (t.confirmedBytes * 8) / elapsedSec / 1_000_000;
  return formatMbps(mbps, unit);
}
