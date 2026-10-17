type AxiosLike = {
  response?: {
    status?: number;
    data?: { code?: string; message?: string };
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
  if (code === "INVALID_RESUME_POLICY") return "Invalid resume policy";
  if (code === "INVALID_SPEED_LIMIT") return "Invalid speed limit";
  return "Transfer action failed";
}

/**
 * Picks an inline error message from a pause, resume, or speed-limit failure.
 */
export function transferActionErrorMessage(
  err: unknown,
  fallback: string,
): string {
  const axiosErr = err as AxiosLike;
  const code = axiosErr.response?.data?.code;
  if (code) return mapTransferErrorCode(code);
  const status = axiosErr.response?.status;
  if (status === 503) return "Agent is offline";
  if (axiosErr.response?.data?.message) return axiosErr.response.data.message;
  return fallback;
}
