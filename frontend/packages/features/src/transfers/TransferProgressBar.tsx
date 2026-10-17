import { Group, Progress, Text } from "@mantine/core";

type TransferProgressBarProps = {
  status: string;
  progress: number;
  bytesLabel: string;
  speedLabel?: string;
  compact?: boolean;
};

/**
 * Progress bar and byte labels for a transfer.
 */
export function TransferProgressBar({
  status,
  progress,
  bytesLabel,
  speedLabel,
  compact = false,
}: TransferProgressBarProps) {
  const color =
    status === "COMPLETED"
      ? "success"
      : status === "PAUSED"
        ? "warning"
        : status === "FAILED"
          ? "danger"
          : "brand";

  return (
    <div>
      <Group justify="space-between" mb={4}>
        <Text size="xs" c="var(--bl-text-muted)">
          {bytesLabel}
        </Text>
        <Text size="xs" c="var(--bl-text-muted)">
          {progress}%
          {speedLabel ? (
            <Text span size="xs" c="var(--bl-primary-text)" ml={8}>
              {speedLabel}
            </Text>
          ) : null}
        </Text>
      </Group>
      <Progress value={progress} color={color} size={compact ? "xs" : "sm"} />
    </div>
  );
}
