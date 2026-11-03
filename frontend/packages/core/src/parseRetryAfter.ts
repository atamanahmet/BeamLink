/**
 * Parses Retry-After as whole seconds. HTTP-date is not supported.
 */
export function parseRetryAfter(value: string | null): number | null {
  if (value == null || value === "") {
    return null;
  }
  if (!/^\d+$/.test(value)) {
    return null;
  }
  const n = Number(value);
  if (!Number.isInteger(n) || n <= 0) {
    return null;
  }
  return n;
}
