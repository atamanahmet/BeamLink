import { ConfirmDialog } from "../settings/ConfirmDialog.tsx";

export type BigDropDialogProps = {
  open: boolean;
  body: string;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * Confirm or cancel a big drop. Same chrome as ConfirmDialog.
 */
export function BigDropDialog({
  open,
  body,
  onConfirm,
  onCancel,
}: BigDropDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      title="Send these files?"
      body={body}
      confirmLabel="Confirm"
      confirmColor="danger"
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
