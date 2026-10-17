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
import { requestRename } from "../api/agentApi";
import { useAgentStatusStore } from "../store/agentStatusStore";

export const AgentInfo = () => {
  const agentStatus = useAgentStatusStore((s) => s.agentStatus);

  const [editing, setEditing] = useState(false);
  const [newName, setNewName] = useState("");
  const [requestMessage, setRequestMessage] = useState("");
  const [loading, setLoading] = useState(false);

  /**
   * Asks the nexus to rename this agent, then exits edit mode.
   */
  const handleConfirm = async () => {
    if (!newName.trim()) return;
    try {
      setLoading(true);
      await requestRename(agentStatus!.agentId!, newName);
      setRequestMessage("Name change requested for: " + newName);
      setEditing(false);
    } catch (error) {
      console.error("Rename failed", error);
    } finally {
      setLoading(false);
    }
  };

  if (!agentStatus) {
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
        Agent Information
      </Title>

      <Stack gap="md">
        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Agent Name
          </Text>
          {!editing ? (
            <Group gap="md">
              <Text size="lg" c="var(--bl-text-primary)">
                {agentStatus.name}
              </Text>
              <Button
                variant="subtle"
                color="brand"
                size="compact-sm"
                onClick={() => {
                  setNewName(agentStatus.name);
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
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Agent ID
          </Text>
          <Text size="sm" ff="monospace" c="var(--bl-text-secondary)">
            {agentStatus.agentId ?? "Unregistered"}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Upload Directory
          </Text>
          <Text size="lg" c="var(--bl-text-primary)">
            {agentStatus.uploadDirectory}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Nexus
          </Text>
          <Text size="lg" c="var(--bl-text-primary)">
            {agentStatus.nexusUrl}
          </Text>
        </div>

        <div>
          <Text size="sm" c="var(--bl-primary-text)" mb={4}>
            Status
          </Text>
          <Text size="lg" c="var(--bl-success-text)">
            {agentStatus.status}
          </Text>
        </div>
      </Stack>
    </Paper>
  );
};
