import { ConfirmDialog } from "@beamlink/features";

export type RemoveDeviceConfirmProps = {
  open: boolean;
  name: string;
  pending?: boolean;
  error?: string | null;
  correlationId?: string | null;
  result?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * Asks before removing a registered device.
 */
export function RemoveDeviceConfirm({
  open,
  name,
  pending,
  error,
  correlationId,
  result,
  onConfirm,
  onCancel,
}: RemoveDeviceConfirmProps) {
  return (
    <ConfirmDialog
      open={open}
      title="Remove device"
      body={`Remove ${name}? This removes the device. Its transfer history stays.`}
      confirmLabel="Remove"
      confirmColor="danger"
      pending={pending}
      error={error}
      correlationId={correlationId}
      result={result}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}
