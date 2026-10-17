import { useCallback, useSyncExternalStore } from "react";
import type { SpeedUnit } from "./speedUnits.ts";
import {
  readSpeedUnit,
  writeSpeedUnit,
} from "./unitPreference.ts";

const listeners = new Set<() => void>();

/**
 * Notifies unit preference subscribers after a write.
 */
function emitUnitChange(): void {
  for (const listener of listeners) {
    listener();
  }
}

/**
 * Subscribes to localStorage speed unit changes in this tab.
 */
function subscribe(onStoreChange: () => void): () => void {
  listeners.add(onStoreChange);
  return () => {
    listeners.delete(onStoreChange);
  };
}

/**
 * Speed unit preference backed by localStorage like theme settings.
 */
export function useUnitPreference(): {
  unit: SpeedUnit;
  setUnit: (unit: SpeedUnit) => void;
} {
  const unit = useSyncExternalStore(subscribe, readSpeedUnit, readSpeedUnit);
  const setUnit = useCallback((next: SpeedUnit) => {
    writeSpeedUnit(next);
    emitUnitChange();
  }, []);
  return { unit, setUnit };
}
