import { problemFromUnknown } from "@beamlink/core";
import { notifications } from "@mantine/notifications";
import { createElement } from "react";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";
import { transferActionErrorMessage } from "./transferErrors.ts";

/**
 * Shows a red toast for a failed transfer action.
 */
export function showTransferActionError(
  err: unknown,
  fallback: string,
): void {
  const problem = problemFromUnknown(err);
  notifications.show({
    color: "red",
    message: createElement(ErrorWithRef, {
      message: transferActionErrorMessage(err, fallback),
      correlationId: problem.correlationId,
    }),
  });
}
