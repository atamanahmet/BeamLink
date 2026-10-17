import type { SettingsApi } from "@beamlink/core";
import type { ResumePolicyState } from "./types.ts";

type SetResumePolicy = (
  updater: (prev: ResumePolicyState) => ResumePolicyState,
) => void;

/**
 * Saves the resume policy through SettingsApi.
 */
export async function saveResumePolicyAction(
  settingsApi: SettingsApi,
  resumePolicy: ResumePolicyState,
  setResumePolicy: SetResumePolicy,
): Promise<void> {
  setResumePolicy((prev) => ({ ...prev, save: "saving", error: null }));
  try {
    await settingsApi.putResumePolicy(resumePolicy.value);
    setResumePolicy((prev) => ({ ...prev, save: "success", error: null }));
    setTimeout(
      () => setResumePolicy((prev) => ({ ...prev, save: "idle" })),
      2500,
    );
  } catch {
    setResumePolicy((prev) => ({
      ...prev,
      save: "error",
      error: "Failed to save resume policy.",
    }));
  }
}
