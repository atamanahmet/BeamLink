import type { TransferSummary } from "@beamlink/core";
import { mapTransferErrorCode } from "./transferErrors.ts";

/**
 * True when this device is the receiver of a disk pause.
 */
export function isReceiveDiskPause(
  transfer: TransferSummary,
  localDeviceId: string | null,
): boolean {
  if (localDeviceId == null || localDeviceId.length === 0) {
    return false;
  }
  return (
    transfer.status === "PAUSED" &&
    transfer.failureReason === "INSUFFICIENT_DISK" &&
    transfer.targetDeviceId === localDeviceId
  );
}

/**
 * Title and body from the existing mapper string.
 */
export function diskPauseNotificationCopy(): { title: string; body: string } {
  const text = mapTransferErrorCode("INSUFFICIENT_DISK");
  const i = text.indexOf(". ");
  if (i < 0) {
    return { title: text, body: "" };
  }
  return { title: text.slice(0, i + 1), body: text.slice(i + 2) };
}
