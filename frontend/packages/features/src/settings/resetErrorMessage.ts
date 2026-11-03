import { problemFromUnknown } from "@beamlink/core";

type ResetErr = {
  response?: { status?: number; data?: { count?: number } };
};

/**
 * True when the reset API refused because transfers are still running.
 */
export function isResetBusy(err: unknown): boolean {
  return (err as ResetErr).response?.status === 409;
}

/**
 * Reads a reset API error, including the 409 busy message and count.
 */
export function resetErrorMessage(err: unknown): string {
  const axiosErr = err as ResetErr;
  const problem = problemFromUnknown(err);
  const detail = problem.detail;
  if (axiosErr.response?.status === 409) {
    const count = axiosErr.response.data?.count;
    const base = detail ?? "Cannot reset while a transfer is ACTIVE or PENDING.";
    if (typeof count === "number") {
      return base + " Blocking transfers: " + count + ".";
    }
    return base;
  }
  return detail ?? "Reset failed.";
}
