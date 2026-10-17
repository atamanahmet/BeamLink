import { Clock } from "lucide-react";
import { Center, Loader, Stack, Text, Title } from "@mantine/core";
import { formatDate } from "@beamlink/features";
import {
  useNexusAdminMutations,
  useNexusPendingQuery,
} from "../hooks/useNexusAdmin";
import { ApprovalCard } from "./ApprovalCard";

export const PendingApprovals = () => {
  const { data, isLoading } = useNexusPendingQuery();
  const { approveAgent, rejectAgent, approveRename, rejectRename } =
    useNexusAdminMutations();

  const pendingAgents = data?.agents ?? [];
  const pendingRenames = data?.renames ?? [];

  if (isLoading) {
    return (
      <Center py="xl">
        <Loader color="brand" size="lg" />
      </Center>
    );
  }

  if (pendingAgents.length === 0 && pendingRenames.length === 0) {
    return (
      <Center py="xl">
        <Stack align="center" gap="md">
          <Clock size={64} color="var(--bl-primary)" opacity={0.4} />
          <Text c="var(--bl-text-secondary)">No pending approvals</Text>
        </Stack>
      </Center>
    );
  }

  return (
    <Stack gap="md">
      <Title order={3} c="var(--bl-primary-text)">
        Pending Approvals ({pendingAgents.length + pendingRenames.length})
      </Title>

      <Stack gap="md">
        {pendingAgents.map((agent) => (
          <ApprovalCard
            key={agent.id}
            title={agent.name}
            lines={[
              `IP: ${agent.ipAddress}:${agent.port}`,
              `Requested At: ${formatDate(agent.renameRequestedAt ?? "")}`,
            ]}
            onApprove={() => approveAgent.mutate(agent.id)}
            onReject={() => rejectAgent.mutate(agent.id)}
          />
        ))}
        {pendingRenames.length > 0 ? (
          <>
            <Title order={3} c="var(--bl-primary-text)" mt="md">
              Pending Rename Requests ({pendingRenames.length})
            </Title>

            <Stack gap="md">
              {pendingRenames.map((rename) => (
                <ApprovalCard
                  key={rename.id}
                  title={`${rename.name} → ${rename.requestedName}`}
                  lines={[
                    `Agent ID: ${rename.id}`,
                    `Requested: ${formatDate(rename.renameRequestedAt ?? "")}`,
                  ]}
                  onApprove={() => approveRename.mutate(rename.id)}
                  onReject={() => rejectRename.mutate(rename.id)}
                />
              ))}
            </Stack>
          </>
        ) : null}
      </Stack>
    </Stack>
  );
};
