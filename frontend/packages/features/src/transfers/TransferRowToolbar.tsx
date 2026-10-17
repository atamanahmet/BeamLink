import { ActionIcon, Group, Tooltip } from "@mantine/core";
import { Pause, Play, Trash2, X } from "lucide-react";
import { TransferStatusBadge } from "./TransferStatusBadge.tsx";

export type TransferRowToolbarProps = {
  status: string;
  loading: boolean;
  canPause: boolean;
  showResume: boolean;
  canCancel: boolean;
  canDelete: boolean;
  onPause: () => void;
  onResume: () => void;
  onCancel: () => void;
  onDelete: () => void;
};

/**
 * Status badge and pause, resume, cancel, delete icons.
 */
export function TransferRowToolbar({
  status,
  loading,
  canPause,
  showResume,
  canCancel,
  canDelete,
  onPause,
  onResume,
  onCancel,
  onDelete,
}: TransferRowToolbarProps) {
  return (
    <Group gap="xs" wrap="nowrap" style={{ flexShrink: 0 }}>
      <TransferStatusBadge status={status} />
      {canPause ? (
        <Tooltip label="Pause">
          <ActionIcon
            variant="light"
            color="warning"
            onClick={onPause}
            disabled={loading}
            loading={loading}
            aria-label="Pause"
          >
            <Pause size={14} />
          </ActionIcon>
        </Tooltip>
      ) : null}
      {showResume ? (
        <Tooltip label="Resume">
          <ActionIcon
            variant="light"
            color="brand"
            onClick={onResume}
            disabled={loading}
            loading={loading}
            aria-label="Resume"
          >
            <Play size={14} />
          </ActionIcon>
        </Tooltip>
      ) : null}
      {canCancel ? (
        <Tooltip label="Cancel">
          <ActionIcon
            variant="light"
            color="danger"
            onClick={onCancel}
            disabled={loading}
            loading={loading}
            aria-label="Cancel"
          >
            <X size={14} />
          </ActionIcon>
        </Tooltip>
      ) : null}
      {canDelete ? (
        <Tooltip label="Delete">
          <ActionIcon
            variant="subtle"
            color="danger"
            onClick={onDelete}
            disabled={loading}
            loading={loading}
            aria-label="Delete"
          >
            <Trash2 size={14} />
          </ActionIcon>
        </Tooltip>
      ) : null}
    </Group>
  );
}
