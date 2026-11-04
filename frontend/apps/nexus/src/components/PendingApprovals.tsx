import { Clock } from "lucide-react";
import { Center, Loader, Stack, Text, Title } from "@mantine/core";
import { DEVICE_KEY_LABEL } from "@beamlink/core";
import { formatDate } from "@beamlink/features";
import {
  useNexusAdminMutations,
  useNexusPendingQuery,
} from "../hooks/useNexusAdmin";
import { ApprovalCard } from "./ApprovalCard";

export const PendingApprovals = () => {
  const { data, isLoading } = useNexusPendingQuery();
  const { approveDevice, rejectDevice, approveRename, rejectRename } =
    useNexusAdminMutations();

  const pendingDevices = data?.devices ?? [];
  const pendingRenames = data?.renames ?? [];

  if (isLoading) {
    return (
      <Center py="xl">
        <Loader color="brand" size="lg" />
      </Center>
    );
  }

  if (pendingDevices.length === 0 && pendingRenames.length === 0) {
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
        Pending Approvals ({pendingDevices.length + pendingRenames.length})
      </Title>

      <Stack gap="md">
        {pendingDevices.map((device) => (
          <ApprovalCard
            key={device.id}
            title={device.name}
            lines={[
              `IP: ${device.ipAddress}:${device.port}`,
              `${DEVICE_KEY_LABEL}: ${device.fingerprint ?? ""}`,
              `Requested At: ${formatDate(device.registeredAt ?? "")}`,
            ]}
            approvePreview={{
              name: device.name,
              ip: `${device.ipAddress}:${device.port}`,
              fingerprint: device.fingerprint ?? "",
            }}
            onApprove={() => approveDevice.mutate(device.id)}
            onReject={() => rejectDevice.mutate(device.id)}
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
                    `Device ID: ${rename.id}`,
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
