import { useState } from "react";
import { Wifi, WifiOff, Trash2, RefreshCw, Users } from "lucide-react";
import {
  Badge,
  Button,
  Center,
  Group,
  Loader,
  Paper,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { formatDate } from "@beamlink/features";
import {
  useNexusAdminMutations,
  useNexusAgentsQuery,
} from "../hooks/useNexusAdmin";
import { RemoveAgentConfirm } from "./RemoveAgentConfirm";

export const AgentList = () => {
  const { data: agents = [], isLoading, refetch, isFetching } =
    useNexusAgentsQuery();
  const { removeAgent } = useNexusAdminMutations();
  const [pendingRemove, setPendingRemove] = useState<{
    id: string;
    name: string;
  } | null>(null);

  if (isLoading) {
    return (
      <Center py="xl">
        <Loader color="brand" size="lg" />
      </Center>
    );
  }

  if (agents.length === 0) {
    return (
      <Center py="xl">
        <Stack align="center" gap="md">
          <Users size={64} color="var(--bl-primary)" opacity={0.4} />
          <Text c="var(--bl-text-secondary)">No agents registered yet</Text>
        </Stack>
      </Center>
    );
  }

  return (
    <Stack gap="md">
      <Group justify="space-between">
        <Title order={3} c="var(--bl-primary-text)">
          Registered Agents ({agents.length})
        </Title>
        <Button
          variant="light"
          color="brand"
          size="compact-sm"
          leftSection={<RefreshCw size={16} />}
          onClick={() => void refetch()}
          loading={isFetching}
        >
          Refresh
        </Button>
      </Group>

      <Group grow preventGrowOverflow={false} align="stretch" wrap="wrap">
        {agents.map((agent) => (
          <Paper key={agent.id} withBorder p="md" radius="md" maw={360} w="100%">
            <Stack gap="sm">
              <Group justify="space-between">
                <Group gap="xs">
                  {agent.online ? <Wifi size={20} /> : <WifiOff size={20} />}
                  <Badge
                    variant="light"
                    color={agent.online ? "success" : "danger"}
                    size="sm"
                  >
                    {agent.online ? "Online" : "Offline"}
                  </Badge>
                </Group>
                <Text size="xs" c="var(--bl-text-secondary)">
                  {agent.fileCount} files
                </Text>
              </Group>

              <Title order={4} c="var(--bl-text-primary)" lineClamp={1}>
                {agent.name}
              </Title>

              <Stack gap={2}>
                <Text size="sm" c="var(--bl-text-secondary)">
                  IP: {agent.ipAddress}:{agent.port}
                </Text>
                <Text size="sm" c="var(--bl-text-secondary)">
                  Last seen: {formatDate(agent.lastSeen ?? "")}
                </Text>
              </Stack>

              <Button
                variant="subtle"
                color="danger"
                size="compact-sm"
                leftSection={<Trash2 size={16} />}
                onClick={() =>
                  setPendingRemove({ id: agent.id, name: agent.name })
                }
              >
                Remove Agent
              </Button>
            </Stack>
          </Paper>
        ))}
      </Group>

      <RemoveAgentConfirm
        open={pendingRemove != null}
        name={pendingRemove?.name ?? ""}
        onCancel={() => setPendingRemove(null)}
        onConfirm={() => {
          if (pendingRemove == null) return;
          removeAgent.mutate(pendingRemove.id);
          setPendingRemove(null);
        }}
      />
    </Stack>
  );
};
