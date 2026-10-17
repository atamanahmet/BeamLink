import type { SettingsApi } from "@beamlink/core";
import type { SpeedCapState } from "./types.ts";

type SetSpeedCap = (updater: (prev: SpeedCapState) => SpeedCapState) => void;

/**
 * Saves the speed cap through SettingsApi.
 */
export async function saveSpeedCapAction(
  settingsApi: SettingsApi,
  speedCap: SpeedCapState,
  setSpeedCap: SetSpeedCap,
): Promise<void> {
  const parsed = parseFloat(speedCap.value);
  if (isNaN(parsed) || parsed < 1 || parsed > 1000) {
    setSpeedCap((prev) => ({
      ...prev,
      error: "Enter a value between 1 and 1000 Mbps.",
    }));
    return;
  }

  setSpeedCap((prev) => ({ ...prev, save: "saving", error: null }));
  try {
    await settingsApi.putSpeedCap(parsed);
    setSpeedCap((prev) => ({ ...prev, save: "success", error: null }));
    setTimeout(() => setSpeedCap((prev) => ({ ...prev, save: "idle" })), 2500);
  } catch {
    setSpeedCap((prev) => ({
      ...prev,
      save: "error",
      error: "Failed to save speed cap.",
    }));
  }
}
