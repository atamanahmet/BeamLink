import { Check, X } from "lucide-react";
import { Button, Group, Paper, Stack, Text, Title } from "@mantine/core";

export type ApprovalCardProps = {
  title: string;
  lines: string[];
  onApprove: () => void;
  onReject: () => void;
};

/**
 * One pending agent or rename approval row.
 */
export function ApprovalCard({
  title,
  lines,
  onApprove,
  onReject,
}: ApprovalCardProps) {
  return (
    <Paper withBorder p="md" radius="md">
      <Group justify="space-between" align="center" wrap="wrap">
        <Stack gap={4}>
          <Title order={4} c="var(--bl-text-primary)">
            {title}
          </Title>
          {lines.map((line) => (
            <Text key={line} size="sm" c="var(--bl-text-secondary)">
              {line}
            </Text>
          ))}
        </Stack>

        <Group gap="sm">
          <Button
            color="success"
            leftSection={<Check size={16} />}
            onClick={onApprove}
          >
            Approve
          </Button>
          <Button
            color="danger"
            leftSection={<X size={16} />}
            onClick={onReject}
          >
            Reject
          </Button>
        </Group>
      </Group>
    </Paper>
  );
}
