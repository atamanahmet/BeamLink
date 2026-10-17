import { Button, Group, Text } from "@mantine/core";

type BrowserFooterProps = {
  hint: string;
  confirmLabel: string;
  canConfirm: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

/**
 * Hint text plus cancel and confirm actions.
 */
export function BrowserFooter({
  hint,
  confirmLabel,
  canConfirm,
  onCancel,
  onConfirm,
}: BrowserFooterProps) {
  return (
    <Group
      justify="space-between"
      px="md"
      py="xs"
      style={{
        borderTop: "1px solid var(--bl-border)",
        backgroundColor: "var(--bl-surface)",
      }}
    >
      <Text size="xs" c="var(--bl-text-muted)">
        {hint}
      </Text>
      <Group gap="xs">
        <Button variant="subtle" color="brand" onClick={onCancel}>
          Cancel
        </Button>
        <Button onClick={onConfirm} disabled={!canConfirm}>
          {confirmLabel}
        </Button>
      </Group>
    </Group>
  );
}
