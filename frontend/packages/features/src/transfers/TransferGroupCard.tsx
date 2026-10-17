import type { ReactNode } from "react";
import {
  ActionIcon,
  Badge,
  Box,
  Group,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { ChevronDown, ChevronUp } from "lucide-react";
import { formatBytes, formatDate } from "./transferFormat.ts";
import { TransferProgressBar } from "./TransferProgressBar.tsx";
import { TransferStatusBadge } from "./TransferStatusBadge.tsx";

export type TransferGroupCardProps = {
  typeLabels: string;
  status: string;
  totalSize: number;
  confirmedBytes: number;
  transferCount: number;
  createdAt: string;
  peerName: string;
  peerOnline: boolean;
  progress: number;
  isOpen: boolean;
  onToggle: () => void;
  children?: ReactNode;
};

/**
 * Dispatch group card wrapping multiple transfers.
 */
export function TransferGroupCard({
  typeLabels,
  status,
  totalSize,
  confirmedBytes,
  transferCount,
  createdAt,
  peerName,
  peerOnline,
  progress,
  isOpen,
  onToggle,
  children,
}: TransferGroupCardProps) {
  const showProgress = ["ACTIVE", "PAUSED", "COMPLETED"].includes(status);

  return (
    <Paper
      withBorder
      p="md"
      radius="md"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Group justify="space-between" align="flex-start" gap="md" wrap="nowrap">
        <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
          <Group gap="xs" wrap="nowrap">
            <Badge variant="light" color="brand" size="sm">
              Multiple
            </Badge>
            <Text fw={500} c="var(--bl-text-primary)">
              {typeLabels}
            </Text>
            <ActionIcon
              variant="subtle"
              color="brand"
              size="sm"
              onClick={onToggle}
              aria-label={isOpen ? "Collapse" : "Expand"}
            >
              {isOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </ActionIcon>
          </Group>
          <Group gap="xs">
            <Text size="xs" c="var(--bl-text-muted)">
              {formatBytes(totalSize)}
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
              ·
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
              {transferCount} transfers
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
              ·
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
              {formatDate(createdAt)}
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
              ·
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
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
          </Group>
        </Stack>
        <TransferStatusBadge status={status} />
      </Group>

      {showProgress ? (
        <Box mt="sm">
          <TransferProgressBar
            status={status}
            progress={progress}
            bytesLabel={`${formatBytes(confirmedBytes)} / ${formatBytes(totalSize)}`}
          />
        </Box>
      ) : null}

      {isOpen && children ? (
        <Stack gap="sm" mt="sm" pt="sm" style={{ borderTop: "1px solid var(--bl-border)" }}>
          {children}
        </Stack>
      ) : null}
    </Paper>
  );
}
