import { Button, Loader, Paper, Stack, Text, Group } from "@mantine/core";
import { ScrollText } from "lucide-react";
import type { LogState } from "./types.ts";
import { StatusMessage } from "./StatusMessage.tsx";

type BackendLogsSectionProps = {
  log: LogState;
  onOpenLogs: () => void;
};

/**
 * Backend logs open action and error message.
 */
export function BackendLogsSection({
  log,
  onOpenLogs,
}: BackendLogsSectionProps) {
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
      <Stack gap="md">
        <Group gap="xs">
          <ScrollText size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Backend Logs
          </Text>
        </Group>

        <Text size="sm" c="var(--bl-text-muted)">
          Opens the backend log file in your system's default application.
        </Text>

        <Stack gap="sm">
          <Button
            variant="light"
            color="brand"
            onClick={onOpenLogs}
            disabled={log.opening}
            w="fit-content"
            leftSection={
              log.opening ? (
                <Loader size={16} color="brand" type="dots" />
              ) : (
                <ScrollText size={16} />
              )
            }
          >
            Open Logs
          </Button>
          {log.error ? (
            <StatusMessage type="error" message={log.error} />
          ) : null}
        </Stack>
      </Stack>
    </Paper>
  );
}
