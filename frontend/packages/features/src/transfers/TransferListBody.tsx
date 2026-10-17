import type { ReactNode } from "react";
import { Stack } from "@mantine/core";
import type { PeerSummary, TransferSummary } from "@beamlink/core";
import { ElapsedLabel } from "./ElapsedLabel.tsx";
import { TransferChildRow } from "./TransferChildRow.tsx";
import { TransferGroupCard } from "./TransferGroupCard.tsx";
import { TransferRow } from "./TransferRow.tsx";
import type { SpeedUnit } from "./speedUnits.ts";
import { formatBytes, TYPE_LABEL } from "./transferFormat.ts";
import {
  formatElapsedMs,
  formatSpeed,
  type DispatchGroup,
} from "./transferGrouping.ts";
import { resolveTransferPeerId } from "./resolveTransferPeerId.ts";

type TransferListBodyProps = {
  items: Array<
    | { kind: "standalone"; t: TransferSummary; createdAt: number }
    | { kind: "group"; g: DispatchGroup; createdAt: number }
  >;
  peers: PeerSummary[];
  unit: SpeedUnit;
  localAgentId?: string | null;
  actionLoading: Record<string, boolean>;
  inlineErrors: Record<string, string>;
  expanded: Record<string, boolean>;
  childrenMap: Record<string, TransferSummary[]>;
  groupExpanded: Record<string, boolean>;
  onToggleExpand: (t: TransferSummary) => void;
  onToggleGroup: (dispatchId: string) => void;
  onPause: (t: TransferSummary) => void;
  onResume: (t: TransferSummary) => void;
  onCancel: (t: TransferSummary) => void;
  onDelete: (t: TransferSummary) => void;
  onSetSpeedLimit: (t: TransferSummary, limitMbps: number | null) => void;
};

/**
 * Builds the elapsed label for one transfer row.
 */
function elapsedFor(t: TransferSummary): ReactNode {
  if (t.status === "PENDING") {
    return <ElapsedLabel createdAt={t.createdAt} />;
  }
  if (t.status === "COMPLETED" && t.completedAt) {
    return formatElapsedMs(
      new Date(t.completedAt).getTime() - new Date(t.createdAt).getTime(),
    );
  }
  if (t.status === "ACTIVE") {
    return formatElapsedMs(Date.now() - new Date(t.createdAt).getTime());
  }
  return "-";
}

/**
 * Renders grouped and standalone transfer rows.
 */
export function TransferListBody({
  items,
  peers,
  unit,
  localAgentId = null,
  actionLoading,
  inlineErrors,
  expanded,
  childrenMap,
  groupExpanded,
  onToggleExpand,
  onToggleGroup,
  onPause,
  onResume,
  onCancel,
  onDelete,
  onSetSpeedLimit,
}: TransferListBodyProps) {
  const getPeer = (agentId: string) => peers.find((p) => p.id === agentId);

  const renderTransferRow = (t: TransferSummary, indented = false) => {
    const peerId = resolveTransferPeerId(
      localAgentId,
      t.sourceAgentId,
      t.targetAgentId,
    );
    const peer = peerId ? getPeer(peerId) : undefined;
    const isExpanded = !!expanded[t.id];
    const progress =
      t.totalSize > 0
        ? Math.min(100, Math.round((t.confirmedBytes / t.totalSize) * 100))
        : 0;

    return (
      <TransferRow
        key={t.id}
        transfer={t}
        actions={{
          peerName: peer?.agentName ?? peerId?.slice(0, 8) ?? "-",
          peerOnline: peer?.online ?? false,
          indented,
          progress,
          elapsedLabel: elapsedFor(t),
          speedLabel: formatSpeed(t, unit),
          loading: !!actionLoading[t.id],
          isExpanded,
          inlineError: inlineErrors[t.id],
          unit,
          localAgentId,
          onExpand: () => onToggleExpand(t),
          onPause: () => onPause(t),
          onResume: () => onResume(t),
          onCancel: () => onCancel(t),
          onDelete: () => onDelete(t),
          onSetSpeedLimit: (limitMbps) => onSetSpeedLimit(t, limitMbps),
          childRows:
            isExpanded && childrenMap[t.id]
              ? childrenMap[t.id].map((child) => {
                  const childProgress =
                    child.totalSize > 0
                      ? Math.min(
                          100,
                          Math.round(
                            (child.confirmedBytes / child.totalSize) * 100,
                          ),
                        )
                      : 0;
                  return (
                    <TransferChildRow
                      key={child.id}
                      name={child.name}
                      status={child.status}
                      progress={childProgress}
                      bytesLabel={`${formatBytes(child.confirmedBytes)} / ${formatBytes(child.totalSize)}`}
                    />
                  );
                })
              : undefined,
        }}
      />
    );
  };

  const renderDispatchGroup = (group: DispatchGroup) => {
    const peer = getPeer(group.targetAgentId);
    const isOpen = !!groupExpanded[group.dispatchId];
    const progress =
      group.totalSize > 0
        ? Math.min(
            100,
            Math.round((group.confirmedBytes / group.totalSize) * 100),
          )
        : 0;

    return (
      <TransferGroupCard
        key={group.dispatchId}
        typeLabels={group.transfers.map((t) => TYPE_LABEL[t.type]).join(" + ")}
        status={group.status}
        totalSize={group.totalSize}
        confirmedBytes={group.confirmedBytes}
        transferCount={group.transfers.length}
        createdAt={group.createdAt}
        peerName={peer?.agentName ?? group.targetAgentId?.slice(0, 8) ?? "-"}
        peerOnline={peer?.online ?? false}
        progress={progress}
        isOpen={isOpen}
        onToggle={() => onToggleGroup(group.dispatchId)}
      >
        {isOpen
          ? group.transfers.map((t) => renderTransferRow(t, true))
          : null}
      </TransferGroupCard>
    );
  };

  return (
    <Stack gap="sm">
      {items.map((item) =>
        item.kind === "standalone"
          ? renderTransferRow(item.t)
          : renderDispatchGroup(item.g),
      )}
    </Stack>
  );
}
