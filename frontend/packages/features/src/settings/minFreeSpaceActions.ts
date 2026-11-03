import { gibToBytes, problemFromUnknown } from "@beamlink/core";
import type { SettingsApi } from "@beamlink/core";
import type { MinFreeSpaceState } from "./types.ts";

type SetMinFreeSpace = (
  updater: (prev: MinFreeSpaceState) => MinFreeSpaceState,
) => void;

/**
 * Saves the disk reserve as whole bytes.
 */
export async function saveMinFreeSpaceAction(
  settingsApi: SettingsApi,
  minFreeSpace: MinFreeSpaceState,
  setMinFreeSpace: SetMinFreeSpace,
): Promise<void> {
  const parsed = parseFloat(minFreeSpace.value);
  if (isNaN(parsed) || parsed < 0.25 || parsed > 1024) {
    setMinFreeSpace((prev) => ({
      ...prev,
      error: "Enter a value between 0.25 and 1024 GiB.",
      correlationId: null,
    }));
    return;
  }

  setMinFreeSpace((prev) => ({
    ...prev,
    save: "saving",
    error: null,
    correlationId: null,
  }));
  try {
    await settingsApi.putMinFreeSpace(gibToBytes(parsed));
    setMinFreeSpace((prev) => ({
      ...prev,
      save: "success",
      error: null,
      correlationId: null,
    }));
    setTimeout(
      () => setMinFreeSpace((prev) => ({ ...prev, save: "idle" })),
      2500,
    );
  } catch (err: unknown) {
    const problem = problemFromUnknown(err);
    setMinFreeSpace((prev) => ({
      ...prev,
      save: "error",
      error: problem.detail ?? "Failed to save disk reserve.",
      correlationId: problem.correlationId,
    }));
  }
}
