import { useState } from "react";
import {
  Button,
  Group,
  Paper,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { DEVICE_KEY_LABEL, problemFromUnknown } from "@beamlink/core";
import { ConnectBanner, ErrorWithRef, useConnectOpen } from "@beamlink/features";
import { requestRename } from "../api/deviceApi";
import { useDeviceStatusStore } from "../store/deviceStatusStore";

export const DeviceInfo = () => {
  const deviceStatus = useDeviceStatusStore((s) => s.deviceStatus);
  const openConnect = useConnectOpen();

  const [editing, setEditing] = useState(false);
  const [newName, setNewName] = useState("");
  const [requestMessage, setRequestMessage] = useState("");
  const [renameError, setRenameError] = useState("");
  const [renameCorrelationId, setRenameCorrelationId] = useState<string | null>(
    null,
  );
  const [loading, setLoading] = useState(false);

  /**
   * Asks the nexus to rename this device, then exits edit mode.
   */
  const handleConfirm = async () => {
    if (!newName.trim()) return;
    try {
      setLoading(true);
      setRenameError("");
      setRenameCorrelationId(null);
      await requestRename(deviceStatus!.deviceId!, newName);
      setRequestMessage("Name change requested for: " + newName);
      setEditing(false);
    } catch (err: unknown) {
      const problem = problemFromUnknown(err);
      setRenameError(problem.detail ?? "Rename failed.");
      setRenameCorrelationId(problem.correlationId);
    } finally {
      setLoading(false);
    }
  };

  if (!deviceStatus) {
    return (
      <Paper
        withBorder
        p="xl"
        radius="md"
        maw={896}
        mx="auto"
        w="100%"
        style={{
          backgroundColor: "var(--bl-surface)",
          borderColor: "var(--bl-border)",
        }}
      >
        <Text c="var(--bl-primary-text)">Loading...</Text>
      </Paper>
    );
  }

  return (
    <Paper
      withBorder
      p="xl"
      radius="md"
      maw={896}
      mx="auto"
      w="100%"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Title order={2} c="var(--bl-text-primary)" mb="md">
        Device Information
      </Title>

      <Stack gap="md">
        <ConnectBanner state={deviceStatus.state} onOpen={openConnect} />
        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Device Name
          </Text>
          {!editing ? (
            <Group gap="md">
              <Text size="lg" c="var(--bl-text-primary)">
                {deviceStatus.name}
              </Text>
              <Button
                variant="subtle"
                color="brand"
                size="compact-sm"
                onClick={() => {
                  setNewName(deviceStatus.name);
                  setEditing(true);
                }}
              >
                Edit
              </Button>
              {requestMessage ? (
                <Text size="sm" c="var(--bl-text-secondary)">
                  {requestMessage}
                </Text>
              ) : null}
            </Group>
          ) : (
            <Group gap="sm">
              <TextInput
                value={newName}
                onChange={(e) => setNewName(e.currentTarget.value)}
              />
              <Button onClick={handleConfirm} disabled={loading}>
                {loading ? "Saving..." : "Confirm"}
              </Button>
              <Button
                variant="subtle"
                color="gray"
                onClick={() => setEditing(false)}
              >
                Cancel
              </Button>
            </Group>
          )}
          {renameError ? (
            <ErrorWithRef
              message={renameError}
              correlationId={renameCorrelationId}
            />
          ) : null}
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Device ID
          </Text>
          <Text size="sm" ff="monospace" c="var(--bl-text-secondary)">
            {deviceStatus.deviceId ?? "Unregistered"}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Upload Directory
          </Text>
          <Text size="lg" c="var(--bl-text-primary)">
            {deviceStatus.uploadDirectory}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Nexus
          </Text>
          <Text size="lg" c="var(--bl-text-primary)">
            {deviceStatus.nexusUrl}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            {DEVICE_KEY_LABEL}
          </Text>
          <Text size="sm" ff="monospace" c="var(--bl-text-secondary)">
            {deviceStatus.fingerprint ?? ""}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Status
          </Text>
          <Text size="lg" c="var(--bl-success-text)">
            {deviceStatus.status}
          </Text>
        </div>
      </Stack>
    </Paper>
  );
};
