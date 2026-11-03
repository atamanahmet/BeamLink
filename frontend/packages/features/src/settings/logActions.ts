import { problemFromUnknown } from "@beamlink/core";
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
  setLog({ opening: true, error: null, correlationId: null });
  try {
    await settingsApi.openLogs();
    setLog({ opening: false, error: null, correlationId: null });
  } catch (err: unknown) {
    const problem = problemFromUnknown(err);
    setLog({
      opening: false,
      error: problem.detail ?? "Failed to open log file.",
      correlationId: problem.correlationId,
    });
  }
}
