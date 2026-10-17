import { Stack, Text } from "@mantine/core";
import { Inbox } from "lucide-react";

/**
 * Empty state when there are no transfers.
 */
export function TransfersEmpty() {
  return (
    <Stack align="center" gap="sm" py={48}>
      <Inbox size={28} color="var(--bl-text-muted)" />
      <Text c="var(--bl-text-muted)" ta="center">
        No transfers yet
      </Text>
    </Stack>
  );
}
