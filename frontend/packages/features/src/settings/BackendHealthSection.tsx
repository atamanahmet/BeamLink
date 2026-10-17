import { Group, Loader, Paper, Stack, Text } from "@mantine/core";
import { Activity } from "lucide-react";

type BackendHealthSectionProps = {
  backendOnline: boolean | null;
};

/**
 * Shows backend online or offline status.
 */
export function BackendHealthSection({
  backendOnline,
}: BackendHealthSectionProps) {
  return (
    <Paper
      withBorder
      p="lg"
      radius="md"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Stack gap="sm">
        <Group gap="xs">
          <Activity size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Backend
          </Text>
        </Group>
        {backendOnline === null ? (
          <Group gap={4}>
            <Loader size={12} color="brand" type="dots" />
            <Text size="xs" c="var(--bl-text-muted)">
              Checking...
            </Text>
          </Group>
        ) : backendOnline ? (
          <Group gap={6}>
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                backgroundColor: "var(--bl-success)",
                display: "inline-block",
              }}
            />
            <Text size="xs" c="var(--bl-success-text)">
              Online
            </Text>
          </Group>
        ) : (
          <Group gap={6}>
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                backgroundColor: "var(--bl-danger)",
                display: "inline-block",
              }}
            />
            <Text size="xs" c="var(--bl-danger-text)">
              Offline
            </Text>
          </Group>
        )}
      </Stack>
    </Paper>
  );
}
