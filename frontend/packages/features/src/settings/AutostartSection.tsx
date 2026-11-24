import { Group, Paper, Stack, Switch, Text } from "@mantine/core";
import { StatusMessage } from "./StatusMessage.tsx";

export type AutostartSectionProps = {
  enabled: boolean;
  onChange: (enabled: boolean) => void;
  disabled?: boolean;
  error?: string | null;
  correlationId?: string | null;
};

/**
 * Start with the system switch for settings and first run.
 */
export function AutostartSection({
  enabled,
  onChange,
  disabled = false,
  error = null,
  correlationId = null,
}: AutostartSectionProps) {
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
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Stack gap={4}>
            <Text size="sm" fw={600} c="var(--bl-text-primary)">
              Start with the system
            </Text>
            <Text size="xs" c="var(--bl-text-muted)">
              Starts minimized to the tray
            </Text>
          </Stack>
          <Switch
            checked={enabled}
            disabled={disabled}
            onChange={(event) => onChange(event.currentTarget.checked)}
            aria-label="Start with the system"
          />
        </Group>
        {error ? (
          <StatusMessage
            type="error"
            message={error}
            correlationId={correlationId}
          />
        ) : null}
      </Stack>
    </Paper>
  );
}
