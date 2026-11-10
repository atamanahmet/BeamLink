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
import {
  DEVICE_KEY_LABEL,
  NEXUS_CERTIFICATE_LABEL,
  problemFromUnknown,
} from "@beamlink/core";
import {
  FingerprintText,
  formatDate,
  formatDeviceAddress,
} from "@beamlink/features";
import {
  useNexusAdminMutations,
  useNexusDevicesQuery,
  useNexusStatsQuery,
} from "../hooks/useNexusAdmin";
import { RemoveDeviceConfirm } from "./RemoveDeviceConfirm";

export const DeviceList = () => {
  const { data: devices = [], isLoading, refetch, isFetching } =
    useNexusDevicesQuery();
  const { data: stats } = useNexusStatsQuery();
  const { removeDevice } = useNexusAdminMutations();
  const [pendingRemove, setPendingRemove] = useState<{
    id: string;
    name: string;
  } | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const [removeCorrelationId, setRemoveCorrelationId] = useState<string | null>(
    null,
  );
  const [removeResult, setRemoveResult] = useState<string | null>(null);

  if (isLoading) {
    return (
      <Center py="xl">
        <Loader color="brand" size="lg" />
      </Center>
    );
  }

  const fingerprint = stats?.tlsFingerprint ? (
    <FingerprintText
      value={stats.tlsFingerprint}
      label={NEXUS_CERTIFICATE_LABEL}
    />
  ) : null;

  if (devices.length === 0) {
    return (
      <Stack gap="md">
        {fingerprint}
        <Center py="xl">
          <Stack align="center" gap="md">
            <Users size={64} color="var(--bl-primary)" opacity={0.4} />
            <Text c="var(--bl-text-secondary)">No devices registered yet</Text>
          </Stack>
        </Center>
      </Stack>
    );
  }

  return (
    <Stack gap="md">
      {fingerprint}
      <Group justify="space-between">
        <Title order={3} c="var(--bl-primary-text)">
          Registered Devices ({devices.length})
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
        {devices.map((device) => (
          <Paper key={device.id} withBorder p="md" radius="md" maw={360} w="100%">
            <Stack gap="sm">
              <Group justify="space-between">
                <Group gap="xs">
                  {device.online ? <Wifi size={20} /> : <WifiOff size={20} />}
                  <Badge
                    variant="light"
                    color={device.online ? "success" : "danger"}
                    size="sm"
                  >
                    {device.online ? "Online" : "Offline"}
                  </Badge>
                </Group>
                <Text size="xs" c="var(--bl-text-secondary)">
                  {device.fileCount} files
                </Text>
              </Group>

              <Title order={4} c="var(--bl-text-primary)" lineClamp={1}>
                {device.name}
              </Title>

              <Stack gap={2}>
                <Text size="sm" c="var(--bl-text-secondary)">
                  IP: {formatDeviceAddress(device.ipAddress, device.port, device.tlsPort)}
                </Text>
                <FingerprintText
                  label={DEVICE_KEY_LABEL}
                  value={device.fingerprint ?? ""}
                />
                <Text size="sm" c="var(--bl-text-secondary)">
                  Last seen: {formatDate(device.lastSeen ?? "")}
                </Text>
              </Stack>

              <Button
                variant="subtle"
                color="danger"
                size="compact-sm"
                leftSection={<Trash2 size={16} />}
                onClick={() => {
                  setRemoveError(null);
                  setRemoveCorrelationId(null);
                  setRemoveResult(null);
                  setPendingRemove({ id: device.id, name: device.name });
                }}
              >
                Remove Device
              </Button>
            </Stack>
          </Paper>
        ))}
      </Group>

      <RemoveDeviceConfirm
        open={pendingRemove != null}
        name={pendingRemove?.name ?? ""}
        pending={removeDevice.isPending}
        error={removeError}
        correlationId={removeCorrelationId}
        result={removeResult}
        onCancel={() => {
          if (removeDevice.isPending) return;
          setPendingRemove(null);
          setRemoveError(null);
          setRemoveCorrelationId(null);
          setRemoveResult(null);
        }}
        onConfirm={() => {
          if (pendingRemove == null) return;
          setRemoveError(null);
          setRemoveCorrelationId(null);
          void removeDevice
            .mutateAsync(pendingRemove.id)
            .then(() => {
              setRemoveResult("Device removed.");
            })
            .catch((err: unknown) => {
              const problem = problemFromUnknown(err);
              setRemoveError(
                problem.detail ??
                  (err instanceof Error ? err.message : "Remove failed."),
              );
              setRemoveCorrelationId(problem.correlationId);
            });
        }}
      />
    </Stack>
  );
};
