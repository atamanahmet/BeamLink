import type { ReactNode } from "react";
import type { TransferSummary } from "@beamlink/core";
import { Box, Group, Paper, Stack, Text } from "@mantine/core";
import type { SpeedUnit } from "./speedUnits.ts";
import { formatBytes } from "./transferFormat.ts";
import { TransferProgressBar } from "./TransferProgressBar.tsx";
import { TransferRowTitle } from "./TransferRowTitle.tsx";
import { TransferRowToolbar } from "./TransferRowToolbar.tsx";
import { TransferSpeedLimitField } from "./TransferSpeedLimitField.tsx";

export type TransferRowActions = {
  peerName: string;
  peerOnline: boolean;
  indented?: boolean;
  progress: number;
  elapsedLabel: ReactNode;
  speedLabel: string;
  loading: boolean;
  isExpanded: boolean;
  inlineError?: string;
  childRows?: ReactNode;
  unit: SpeedUnit;
  localAgentId?: string | null;
  onExpand: () => void;
  onPause: () => void;
  onResume: () => void;
  onCancel: () => void;
  onDelete: () => void;
  onSetSpeedLimit: (limitMbps: number | null) => void;
};

export type TransferRowProps = {
  transfer: TransferSummary;
  actions: TransferRowActions;
};

/**
 * One transfer card with status, progress and actions.
 */
export function TransferRow({ transfer: t, actions }: TransferRowProps) {
  const {
    peerName,
    peerOnline,
    indented = false,
    progress,
    elapsedLabel,
    speedLabel,
    loading,
    isExpanded,
    inlineError,
    childRows,
    unit,
    localAgentId = null,
    onExpand,
    onPause,
    onResume,
    onCancel,
    onDelete,
    onSetSpeedLimit,
  } = actions;

  const canPause = t.status === "ACTIVE";
  const showResume = t.status === "PAUSED" && t.canResume;
  const canCancel = t.status === "ACTIVE" || t.status === "PAUSED";
  const canDelete = [
    "COMPLETED",
    "FAILED",
    "CANCELLED",
    "EXPIRED",
    "PARTIAL",
  ].includes(t.status);
  const showProgress = ["ACTIVE", "PAUSED", "COMPLETED"].includes(t.status);

  return (
    <Paper
      withBorder
      p="md"
      radius="md"
      ml={indented ? "md" : 0}
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Group justify="space-between" align="flex-start" gap="md" wrap="nowrap">
        <TransferRowTitle
          transfer={t}
          peerName={peerName}
          peerOnline={peerOnline}
          showPeer={!indented}
          elapsedLabel={elapsedLabel}
          isExpanded={isExpanded}
          localAgentId={localAgentId}
          onExpand={onExpand}
        />
        <TransferRowToolbar
          status={t.status}
          loading={loading}
          canPause={canPause}
          showResume={showResume}
          canCancel={canCancel}
          canDelete={canDelete}
          onPause={onPause}
          onResume={onResume}
          onCancel={onCancel}
          onDelete={onDelete}
        />
      </Group>

      {showProgress ? (
        <Box mt="sm">
          <TransferProgressBar
            status={t.status}
            progress={progress}
            bytesLabel={`${formatBytes(t.confirmedBytes)} / ${formatBytes(t.totalSize)}`}
            speedLabel={
              t.status === "ACTIVE" ? speedLabel || undefined : undefined
            }
          />
        </Box>
      ) : null}

      {t.type === "SINGLE" ? (
        <Box mt="sm">
          <TransferSpeedLimitField
            unit={unit}
            speedLimitMbps={t.speedLimitMbps}
            effectiveLimitMbps={t.effectiveLimitMbps}
            disabled={loading}
            onSave={onSetSpeedLimit}
          />
        </Box>
      ) : null}

      {isExpanded && childRows ? (
        <Stack
          gap="xs"
          mt="sm"
          pt="sm"
          style={{ borderTop: "1px solid var(--bl-border)" }}
        >
          {childRows}
        </Stack>
      ) : null}

      {t.status === "FAILED" && t.failureReason ? (
        <Text
          mt="xs"
          size="xs"
          c="var(--bl-danger-text)"
          truncate
          ff="monospace"
        >
          {t.failureReason}
        </Text>
      ) : null}
      {inlineError ? (
        <Text mt="xs" size="xs" c="var(--bl-danger-text)">
          {inlineError}
        </Text>
      ) : null}
    </Paper>
  );
}
