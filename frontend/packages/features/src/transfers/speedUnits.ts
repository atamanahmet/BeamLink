export const SPEED_UNITS = ["KB/s", "MB/s", "Mb/s", "Gb/s"] as const;

export type SpeedUnit = (typeof SPEED_UNITS)[number];

export const DEFAULT_SPEED_UNIT: SpeedUnit = "Mb/s";

/**
 * Converts a display value in the given unit to Mb/s.
 */
export function toMbps(value: number, unit: SpeedUnit): number {
  if (unit === "KB/s") return value * 0.008;
  if (unit === "MB/s") return value * 8;
  if (unit === "Gb/s") return value * 1000;
  return value;
}

/**
 * Converts Mb/s to a display value in the given unit.
 */
export function fromMbps(mbps: number, unit: SpeedUnit): number {
  if (unit === "KB/s") return mbps / 0.008;
  if (unit === "MB/s") return mbps / 8;
  if (unit === "Gb/s") return mbps / 1000;
  return mbps;
}

/**
 * Rounds a display speed for labels.
 */
export function roundSpeedDisplay(value: number): string {
  if (!Number.isFinite(value)) return "0";
  const abs = Math.abs(value);
  if (abs >= 100) return value.toFixed(0);
  if (abs >= 10) return value.toFixed(1);
  return value.toFixed(2);
}

/**
 * Formats Mb/s as value plus unit label.
 */
export function formatMbps(mbps: number, unit: SpeedUnit): string {
  return `${roundSpeedDisplay(fromMbps(mbps, unit))} ${unit}`;
}

/**
 * Parses a user input in the given unit to Mb/s. Empty is null.
 */
export function parseSpeedInput(
  raw: string,
  unit: SpeedUnit,
): number | null {
  const trimmed = raw.trim();
  if (trimmed === "") return null;
  const n = Number(trimmed);
  if (!Number.isFinite(n) || n <= 0) return NaN;
  return toMbps(n, unit);
}
