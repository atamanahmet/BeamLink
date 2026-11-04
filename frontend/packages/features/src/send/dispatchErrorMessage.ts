import { mapErrorReason, problemFromUnknown } from "@beamlink/core";
import { mapTransferErrorCode } from "../transfers/transferErrors.ts";

/**
 * Reads a dispatch error message from axios or Error.
 */
export function dispatchErrorMessage(err: unknown): string {
  const problem = problemFromUnknown(err);
  const reasonText = mapErrorReason(problem.reason);
  if (problem.reason === "CONNECT_TIMEOUT" && reasonText) {
    return reasonText;
  }
  if (problem.code === "TARGET_UNREACHABLE" || problem.code === "PEER_UNREACHABLE") {
    return mapTransferErrorCode(problem.code);
  }
  if (problem.detail) {
    return problem.detail;
  }
  if (reasonText) {
    return reasonText;
  }
  if (typeof err === "object" && err !== null) {
    const ax = err as { message?: unknown };
    if (typeof ax.message === "string" && ax.message.length > 0) {
      return ax.message;
    }
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return "Dispatch failed.";
}
