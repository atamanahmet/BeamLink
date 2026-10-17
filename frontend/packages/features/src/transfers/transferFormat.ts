import type { TransferType } from "@beamlink/core";

export const TYPE_LABEL: Record<TransferType, string> = {
  SINGLE: "File",
  BATCH: "Batch",
  DIRECTORY: "Folder",
};

/**
 * Formats a byte count for transfer rows.
 */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/**
 * Formats an ISO date for UI lists. Empty input shows a dash.
 */
export function formatDate(iso: string): string {
  if (!iso) return "—";
  const normalized = iso.replace(/(\.\d{3})\d+/, "$1");
  return new Date(normalized).toLocaleString();
}
