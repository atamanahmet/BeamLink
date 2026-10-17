import type { ReactNode } from "react";
import type { TransferSummary } from "@beamlink/core";
import { ActionIcon, Badge, Group, Stack, Text, Tooltip } from "@mantine/core";
import { ChevronDown, ChevronUp } from "lucide-react";
import { formatBytes, formatDate, TYPE_LABEL } from "./transferFormat.ts";
import { TransferPauseMeta } from "./TransferPauseMeta.tsx";

export type TransferRowTitleProps = {
  transfer: TransferSummary;
  peerName: string;
  peerOnline: boolean;
  showPeer: boolean;
  elapsedLabel: ReactNode;
  isExpanded: boolean;
  localAgentId?: string | null;
  onExpand: () => void;
};

/**
 * Transfer name, size, peer, elapsed, and pause meta.
 */
export function TransferRowTitle({
  transfer: t,
  peerName,
  peerOnline,
  showPeer,
  elapsedLabel,
  isExpanded,
  localAgentId = null,
  onExpand,
}: TransferRowTitleProps) {
  return (
    <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
      <Group gap="xs" wrap="nowrap">
        <Badge variant="light" color="brand" size="sm">
          {TYPE_LABEL[t.type]}
        </Badge>
        <Tooltip label={t.name}>
          <Text
            fw={500}
            c="var(--bl-text-primary)"
            truncate
            style={{ flex: 1, minWidth: 0 }}
          >
            {t.name}
          </Text>
        </Tooltip>
        {t.type !== "SINGLE" ? (
          <ActionIcon
            variant="subtle"
            color="brand"
            size="sm"
            onClick={onExpand}
            aria-label={isExpanded ? "Collapse" : "Expand"}
          >
            {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </ActionIcon>
        ) : null}
      </Group>

      <Group gap="xs" c="var(--bl-text-muted)">
        <Text size="xs">{formatBytes(t.totalSize)}</Text>
        {t.totalFiles > 1 ? (
          <>
            <Text size="xs">·</Text>
            <Text size="xs">{t.totalFiles} files</Text>
          </>
        ) : null}
        <Text size="xs">·</Text>
        <Text size="xs">{formatDate(t.createdAt)}</Text>
        {showPeer ? (
          <>
            <Text size="xs">·</Text>
            <Text size="xs">
              <Text
                span
                c={
                  peerOnline
                    ? "var(--bl-success-text)"
                    : "var(--bl-danger-text)"
                }
              >
                ●
              </Text>{" "}
              {peerName}
            </Text>
          </>
        ) : null}
        <Text size="xs">·</Text>
        <Text size="xs">{elapsedLabel}</Text>
      </Group>

      <Group gap="xs" wrap="wrap">
        <TransferPauseMeta
          status={t.status}
          pausedBy={t.pausedBy}
          canResume={t.canResume}
          peerOnline={peerOnline}
          localAgentId={localAgentId}
          sourceAgentId={t.sourceAgentId}
          targetAgentId={t.targetAgentId}
        />
      </Group>
    </Stack>
  );
}
