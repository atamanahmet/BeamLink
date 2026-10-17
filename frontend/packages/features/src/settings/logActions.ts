import type { SettingsApi } from "@beamlink/core";
import type { LogState } from "./types.ts";

type SetLog = (value: LogState) => void;

/**
 * Opens backend logs through SettingsApi.
 */
export async function openLogsAction(
  settingsApi: SettingsApi,
  setLog: SetLog,
): Promise<void> {
  setLog({ opening: true, error: null });
  try {
    await settingsApi.openLogs();
    setLog({ opening: false, error: null });
  } catch {
    setLog({ opening: false, error: "Failed to open log file." });
  }
}
