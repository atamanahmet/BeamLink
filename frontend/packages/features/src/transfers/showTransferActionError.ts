import { notifications } from "@mantine/notifications";
import { transferActionErrorMessage } from "./transferErrors.ts";

/**
 * Shows a red toast for a failed transfer action.
 */
export function showTransferActionError(
  err: unknown,
  fallback: string,
): void {
  notifications.show({
    color: "red",
    message: transferActionErrorMessage(err, fallback),
  });
}
