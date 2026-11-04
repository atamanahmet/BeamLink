import { useState } from "react";
import { Check, X } from "lucide-react";
import { Button, Group, Paper, Stack, Text, Title } from "@mantine/core";
import { DEVICE_KEY_LABEL } from "@beamlink/core";
import { ConfirmDialog } from "@beamlink/features";

export type ApprovalCardProps = {
  title: string;
  lines: string[];
  approvePreview?: {
    name: string;
    ip: string;
    fingerprint: string;
  };
  onApprove: () => void;
  onReject: () => void;
};

/**
 * One pending device or rename approval row.
 */
export function ApprovalCard({
  title,
  lines,
  approvePreview,
  onApprove,
  onReject,
}: ApprovalCardProps) {
  const [confirmOpen, setConfirmOpen] = useState(false);

  /**
   * Opens the approve dialog when preview data is set.
   */
  const handleApproveClick = () => {
    if (approvePreview) {
      setConfirmOpen(true);
      return;
    }
    onApprove();
  };

  /**
   * Closes the dialog and runs the approve action.
   */
  const handleConfirm = () => {
    setConfirmOpen(false);
    onApprove();
  };

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
            onClick={handleApproveClick}
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
      {approvePreview ? (
        <ConfirmDialog
          open={confirmOpen}
          title={approvePreview.name}
          body={`IP: ${approvePreview.ip}\n${DEVICE_KEY_LABEL}: ${approvePreview.fingerprint}`}
          confirmLabel="Approve"
          onConfirm={handleConfirm}
          onCancel={() => setConfirmOpen(false)}
        />
      ) : null}
    </Paper>
  );
}
