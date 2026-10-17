import { Box, Group, Text, Tooltip } from "@mantine/core";
import { TransferProgressBar } from "./TransferProgressBar.tsx";
import { TransferStatusBadge } from "./TransferStatusBadge.tsx";

type TransferChildRowProps = {
  name: string;
  status: string;
  progress: number;
  bytesLabel: string;
};

/**
 * Nested child file row inside an expanded batch or folder transfer.
 */
export function TransferChildRow({
  name,
  status,
  progress,
  bytesLabel,
}: TransferChildRowProps) {
  return (
    <Box
      px="sm"
      py="xs"
      style={{
        borderRadius: 8,
        border: "1px solid var(--bl-border)",
        backgroundColor: "var(--bl-surface)",
      }}
    >
      <Group justify="space-between" wrap="nowrap" gap="sm" mb={4}>
        <Tooltip label={name}>
          <Text size="xs" c="var(--bl-text-primary)" truncate style={{ flex: 1, minWidth: 0 }}>
            {name}
          </Text>
        </Tooltip>
        <TransferStatusBadge status={status} />
      </Group>
      <TransferProgressBar
        status={status}
        progress={progress}
        bytesLabel={bytesLabel}
        compact
      />
    </Box>
  );
}
