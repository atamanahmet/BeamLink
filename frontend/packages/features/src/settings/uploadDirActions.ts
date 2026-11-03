import { problemFromUnknown } from "@beamlink/core";
import type { SettingsApi } from "@beamlink/core";
import type { UploadDirState } from "./types.ts";

type SetUploadDir = (
  updater: (prev: UploadDirState) => UploadDirState,
) => void;

/**
 * Saves the upload directory through SettingsApi.
 */
export async function saveUploadDirAction(
  settingsApi: SettingsApi,
  path: string,
  setUploadDir: SetUploadDir,
): Promise<void> {
  setUploadDir((prev) => ({
    ...prev,
    save: "saving",
    error: null,
    correlationId: null,
  }));
  try {
    const res = await settingsApi.putUploadDir(path);
    if (res.error) {
      setUploadDir((prev) => ({
        ...prev,
        save: "error",
        error: res.error ?? null,
        correlationId: null,
      }));
      return;
    }
    setUploadDir((prev) => ({
      ...prev,
      current: res.path,
      save: "success",
      error: null,
      correlationId: null,
    }));
    setTimeout(
      () => setUploadDir((prev) => ({ ...prev, save: "idle" })),
      2500,
    );
  } catch (err: unknown) {
    const problem = problemFromUnknown(err);
    const message = problem.detail ?? "Failed to update upload directory.";
    setUploadDir((prev) => ({
      ...prev,
      save: "error",
      error: message,
      correlationId: problem.correlationId,
    }));
  }
}
