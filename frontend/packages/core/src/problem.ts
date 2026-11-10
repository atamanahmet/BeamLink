export type Problem = {
  title: string | null;
  detail: string | null;
  code: string | null;
  reason: string | null;
  correlationId: string | null;
  status: number | null;
};

const EMPTY: Problem = {
  title: null,
  detail: null,
  code: null,
  reason: null,
  correlationId: null,
  status: null,
};

const REASON_TEXT: Record<string, string> = {
  TIMEOUT: "The request timed out.",
  CONNECT_TIMEOUT:
    "Could not reach the other device. If it runs Windows, its firewall may be blocking BeamLink. Allow BeamLink on Private networks (needs an admin once).",
  REFUSED: "The connection was refused.",
  UNKNOWN_HOST: "The host could not be found.",
  TLS_TRUST: "TLS handshake with the peer failed",
  TLS_OTHER: "TLS handshake with the peer failed",
  UNKNOWN: "Something went wrong.",
};

/**
 * Reads ProblemDetail fields. Never throws. Empty or non-JSON bodies stay blank.
 */
export function parseProblem(body: unknown, status?: number | null): Problem {
  try {
    const record = asRecord(body);
    const bodyStatus = record ? asNumber(record.status) : null;
    return {
      title: record ? asText(record.title) : null,
      detail: record ? asText(record.detail) : null,
      code: record ? asText(record.code) : null,
      reason: record ? asText(record.reason) : null,
      correlationId: record ? asText(record.correlationId) : null,
      status: status ?? bodyStatus,
    };
  } catch {
    return { ...EMPTY, status: status ?? null };
  }
}

/**
 * Maps a backend reason code to a short user sentence, or null.
 */
export function mapErrorReason(reason: string | null | undefined): string | null {
  if (reason == null || reason === "") {
    return null;
  }
  return REASON_TEXT[reason] ?? null;
}

/**
 * Reads ProblemDetail from an axios-like error or a bare value. Never throws.
 */
export function problemFromUnknown(err: unknown): Problem {
  try {
    if (typeof err === "object" && err !== null) {
      const ax = err as { response?: { data?: unknown; status?: number } };
      if (ax.response) {
        return parseProblem(ax.response.data, ax.response.status ?? null);
      }
    }
    return parseProblem(undefined, null);
  } catch {
    return { ...EMPTY };
  }
}

/**
 * Turns a JSON body into a string map, or null when it is not an object.
 */
function asRecord(body: unknown): Record<string, unknown> | null {
  if (body == null || body === "") {
    return null;
  }
  if (typeof body === "string") {
    const trimmed = body.trim();
    if (trimmed === "") {
      return null;
    }
    const parsed: unknown = JSON.parse(trimmed);
    if (typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    return null;
  }
  if (typeof body === "object" && !Array.isArray(body)) {
    return body as Record<string, unknown>;
  }
  return null;
}

/**
 * Non-empty string, else null. Never the word undefined.
 */
function asText(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  return value.length > 0 ? value : null;
}

/**
 * Finite number, else null.
 */
function asNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
