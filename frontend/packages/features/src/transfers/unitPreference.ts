import {
  DEFAULT_SPEED_UNIT,
  SPEED_UNITS,
  type SpeedUnit,
} from "./speedUnits.ts";

export const UNIT_STORAGE_KEY = "beamlink-speed-unit";

/**
 * Resolves a stored unit string to a known SpeedUnit.
 */
export function resolveSpeedUnit(raw: string | null): SpeedUnit {
  if (raw != null && (SPEED_UNITS as readonly string[]).includes(raw)) {
    return raw as SpeedUnit;
  }
  return DEFAULT_SPEED_UNIT;
}

/**
 * Reads the speed unit from localStorage.
 */
export function readSpeedUnit(): SpeedUnit {
  try {
    return resolveSpeedUnit(localStorage.getItem(UNIT_STORAGE_KEY));
  } catch {
    return DEFAULT_SPEED_UNIT;
  }
}

/**
 * Writes the speed unit to localStorage.
 */
export function writeSpeedUnit(unit: SpeedUnit): void {
  try {
    localStorage.setItem(UNIT_STORAGE_KEY, unit);
  } catch {
    // Storage may be unavailable.
  }
}
