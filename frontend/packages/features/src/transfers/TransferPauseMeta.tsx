import { Text } from "@mantine/core";
import type { PauseOwner } from "@beamlink/core";
import { resolveTransferPeerId } from "./resolveTransferPeerId.ts";

export type TransferPauseMetaProps = {
  status: string;
  pausedBy: PauseOwner | null;
  canResume: boolean;
  peerOnline: boolean;
  localAgentId?: string | null;
  sourceAgentId?: string | null;
  targetAgentId?: string | null;
};

/**
 * Pause owner text, resume reason, and source-pause warning for target.
 */
export function TransferPauseMeta({
  status,
  pausedBy,
  canResume,
  peerOnline,
  localAgentId = null,
  sourceAgentId = null,
  targetAgentId = null,
}: TransferPauseMetaProps) {
  if (status !== "PAUSED") return null;

  const peerId = resolveTransferPeerId(
    localAgentId,
    sourceAgentId,
    targetAgentId,
  );
  const isTargetSide =
    sourceAgentId != null && peerId != null && peerId === sourceAgentId;

  const ownerLabel =
    pausedBy === "SOURCE"
      ? "Paused by source"
      : pausedBy === "TARGET"
        ? "Paused by target"
        : "Paused";

  let resumeReason: string | null = null;
  if (!canResume) {
    if (!peerOnline) resumeReason = "Agent offline";
    else resumeReason = "Resume not allowed";
  }

  return (
    <>
      <Text size="xs" c="var(--bl-text-secondary)">
        {ownerLabel}
      </Text>
      {resumeReason ? (
        <Text size="xs" c="var(--bl-danger-text)">
          {resumeReason}
        </Text>
      ) : null}
      {isTargetSide && pausedBy === "SOURCE" ? (
        <Text size="xs" c="var(--bl-warning-text)">
          Source paused this transfer
        </Text>
      ) : null}
    </>
  );
}
