/**
 * Groups a device key (32 hex) or TLS pin (64 hex) into 16-hex lines.
 */
export function formatTlsPin(value: string): string {
  const hex = value.replace(/[\s:]/g, "").toLowerCase();
  if (/^[0-9a-f]{64}$/.test(hex)) {
    return [0, 16, 32, 48].map((i) => hex.slice(i, i + 16)).join("\n");
  }
  if (/^[0-9a-f]{32}$/.test(hex)) {
    return [0, 16].map((i) => hex.slice(i, i + 16)).join("\n");
  }
  return value;
}
