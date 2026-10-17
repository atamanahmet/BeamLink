import { Button, Group, Modal, Stack, Text } from "@mantine/core";

export type RemoveAgentConfirmProps = {
  open: boolean;
  name: string;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * Asks before removing a registered agent.
 */
export function RemoveAgentConfirm({
  open,
  name,
  onConfirm,
  onCancel,
}: RemoveAgentConfirmProps) {
  return (
    <Modal
      opened={open}
      onClose={onCancel}
      title="Remove agent"
      centered
      radius="md"
    >
      <Stack gap="md">
        <Text c="var(--bl-text-primary)">
          Remove {name}? This removes the agent. Its transfer history stays.
        </Text>
        <Group justify="flex-end" gap="sm">
          <Button variant="default" onClick={onCancel}>
            Cancel
          </Button>
          <Button color="danger" onClick={onConfirm}>
            Remove
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
