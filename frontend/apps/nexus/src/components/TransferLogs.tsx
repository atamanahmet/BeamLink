import { Activity, ArrowRight, RefreshCw } from "lucide-react";
import {
  Alert,
  Button,
  Center,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { formatBytes, formatDate } from "@beamlink/features";
import { useNexusTransferLogsQuery } from "../hooks/useNexusAdmin";

export const TransferLogs = () => {
  const { data, isLoading, isError, refetch } = useNexusTransferLogsQuery();

  const logs = [...(data ?? [])].sort(
    (a, b) =>
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
  );

  if (isLoading) {
    return (
      <Center py="xl">
        <Loader color="var(--bl-primary)" size="lg" />
      </Center>
    );
  }

  if (isError) {
    return (
      <Alert color="danger" variant="light" ta="center">
        Failed to load transfer logs
      </Alert>
    );
  }

  if (logs.length === 0) {
    return (
      <Stack align="center" gap="md" py="xl">
        <Activity size={64} color="var(--bl-primary)" opacity={0.4} />
        <Text c="var(--bl-text-secondary)">No transfer logs yet</Text>
      </Stack>
    );
  }

  return (
    <Stack gap="md">
      <Group justify="space-between">
        <Title order={3} c="var(--bl-primary-text)">
          Transfer History ({logs.length})
        </Title>

        <Button
          variant="subtle"
          size="compact-sm"
          leftSection={<RefreshCw size={16} />}
          onClick={() => void refetch()}
          c="var(--bl-primary-text)"
        >
          Refresh
        </Button>
      </Group>

      <Table
        highlightOnHover
        withTableBorder
        withColumnBorders={false}
        verticalSpacing="sm"
        style={{
          backgroundColor: "var(--bl-surface)",
          borderColor: "var(--bl-border)",
        }}
      >
        <Table.Tbody>
          {logs.map((log) => (
            <Table.Tr key={log.id}>
              <Table.Td>
                <Group gap="md" wrap="nowrap">
                  <Text c="var(--bl-text-primary)" fw={500}>
                    {log.fromAgentName || "Unknown"}
                  </Text>
                  <ArrowRight size={16} color="var(--bl-primary)" />
                  <Text c="var(--bl-text-primary)" fw={500}>
                    {log.toAgentName || "Unknown"}
                  </Text>
                </Group>
              </Table.Td>
              <Table.Td style={{ textAlign: "right" }}>
                <Text size="sm" c="var(--bl-text-primary)">
                  {log.filename}
                </Text>
                <Text size="xs" c="var(--bl-text-secondary)">
                  {formatBytes(log.fileSize)} • {formatDate(log.timestamp)}
                </Text>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  );
};
