import { BIG_DROP_FILE_LIMIT } from "@beamlink/core";
import { formatBytes } from "../transfers/transferFormat.ts";

export type BigDropCopyInput = {
  names: string[];
  nameCount: number;
  folderCount: number;
  fileCount: number;
  truncated: boolean;
  totalSize: number | null;
  peerName: string;
};

/**
 * Builds the confirm body for a big drop.
 */
export function formatBigDropBody(input: BigDropCopyInput): string {
  const shown = input.names.slice(0, 3);
  const extra = input.nameCount - shown.length;
  let nameLine = shown.join(", ");
  if (extra > 0 && nameLine.length > 0) {
    nameLine = `${nameLine} and ${extra} more`;
  }
  const fileLine = input.truncated
    ? `more than ${BIG_DROP_FILE_LIMIT} files`
    : String(input.fileCount);
  const peer = input.peerName.trim().length > 0 ? input.peerName : "this peer";
  const lines = [
    `Send to ${peer}?`,
    nameLine,
    `Folders: ${input.folderCount}`,
    `Files: ${fileLine}`,
  ];
  if (!input.truncated && input.totalSize !== null) {
    lines.push(`Size: ${formatBytes(input.totalSize)}`);
  }
  return lines.filter((line) => line.length > 0).join("\n");
}
