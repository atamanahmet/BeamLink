import { mapErrorReason, problemFromUnknown } from "@beamlink/core";

type AxiosLike = {
  response?: {
    status?: number;
    data?: unknown;
  };
};

/**
 * Maps backend transfer error codes to short UI messages.
 */
export function mapTransferErrorCode(code: string | undefined): string {
  if (code === "TRANSFER_PAUSED") return "Transfer is paused";
  if (code === "TRANSFER_ALREADY_PAUSED") return "Already paused";
  if (code === "RESUME_FORBIDDEN") return "Resume not allowed";
  if (code === "PEER_UNREACHABLE") return "Peer unreachable";
  if (code === "TARGET_UNREACHABLE")
    return "Target peer is unreachable or has receive turned off";
  if (code === "INVALID_RESUME_POLICY") return "Invalid resume policy";
  if (code === "INVALID_SPEED_LIMIT") return "Invalid speed limit";
  if (code === "CHECKSUM_MISMATCH") {
    return "File was damaged during transfer, send it again";
  }
  if (code === "INSUFFICIENT_DISK") {
    return "Not enough disk space. Free some space, then resume.";
  }
  return "Transfer action failed";
}

/**
 * Picks an inline error message from a pause, resume, or speed-limit failure.
 */
export function transferActionErrorMessage(
  err: unknown,
  fallback: string,
): string {
  const problem = problemFromUnknown(err);
  const reasonText = mapErrorReason(problem.reason);
  if (problem.reason === "CONNECT_TIMEOUT" && reasonText) {
    return reasonText;
  }
  if (problem.code) return mapTransferErrorCode(problem.code);
  const axiosErr = err as AxiosLike;
  const status = problem.status ?? axiosErr.response?.status;
  if (status === 503) return "Peer is offline";
  if (problem.detail) return problem.detail;
  if (reasonText) return reasonText;
  return fallback;
}
