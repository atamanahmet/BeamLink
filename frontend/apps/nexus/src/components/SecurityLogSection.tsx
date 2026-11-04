import { useState } from "react";
import { Button, Group, Paper, Stack, Table, Text } from "@mantine/core";
import { ScrollText } from "lucide-react";
import { problemFromUnknown } from "@beamlink/core";
import { formatDate, StatusMessage } from "@beamlink/features";
import { AUDIT_EVENT_LABELS } from "../auditCopy.ts";
import { useNexusAuditLogQuery } from "../hooks/useNexusAdmin";

const PAGE_SIZE = 50;

/**
 * Paged admin security log. Fetch on mount and page change.
 */
export function SecurityLogSection() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error } = useNexusAuditLogQuery(
    page,
    PAGE_SIZE,
  );
  const rows = data ?? [];
  const problem = isError ? problemFromUnknown(error) : null;

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
            Security log
          </Text>
        </Group>

        {isError ? (
          <StatusMessage
            type="error"
            message={problem?.detail ?? "Could not load the security log."}
            correlationId={problem?.correlationId}
          />
        ) : null}

        {!isError && !isLoading && rows.length === 0 ? (
          <Text size="sm" c="var(--bl-text-muted)">
            No events yet
          </Text>
        ) : null}

        {!isError && rows.length > 0 ? (
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
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Time</Table.Th>
                <Table.Th>Event</Table.Th>
                <Table.Th>Outcome</Table.Th>
                <Table.Th>Source</Table.Th>
                <Table.Th>Detail</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {rows.map((row) => (
                <Table.Tr key={row.id}>
                  <Table.Td>
                    <Text size="sm" c="var(--bl-text-primary)">
                      {formatDate(row.occurredAt)}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="var(--bl-text-primary)">
                      {AUDIT_EVENT_LABELS[row.eventType] ?? row.eventType}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text
                      size="sm"
                      c={
                        row.outcome === "FAILED"
                          ? "var(--bl-danger-text)"
                          : "var(--bl-text-secondary)"
                      }
                    >
                      {row.outcome}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="var(--bl-text-secondary)">
                      {row.sourceAddress ?? "-"}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="var(--bl-text-secondary)">
                      {row.detail ?? "-"}
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        ) : null}

        <Group gap="sm">
          <Button
            variant="light"
            size="compact-sm"
            disabled={page === 0 || isLoading}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </Button>
          <Button
            variant="light"
            size="compact-sm"
            disabled={isLoading || rows.length < PAGE_SIZE}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </Group>
      </Stack>
    </Paper>
  );
}
