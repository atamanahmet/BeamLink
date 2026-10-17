import { useEffect, useState } from "react";
import type { SettingsApi } from "@beamlink/core";
import { saveResumePolicyAction } from "./resumePolicyActions.ts";
import type { ResumePolicyState } from "./types.ts";

export type ResumePolicyControls = {
  value: ResumePolicyState;
  onChangeValue: (value: "OWNER_ONLY" | "ANYONE") => void;
  onSave: () => void;
};

/**
 * Loads and saves resume policy for Nexus settings.
 */
export function useResumePolicySettings(
  settingsApi: SettingsApi,
): ResumePolicyControls {
  const [value, setValue] = useState<ResumePolicyState>({
    value: "OWNER_ONLY",
    save: "idle",
    error: null,
  });

  useEffect(() => {
    let cancelled = false;
    void settingsApi
      .getResumePolicy()
      .then((data) => {
        if (!cancelled) {
          setValue((prev) => ({
            ...prev,
            value: data.resumePolicy,
          }));
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [settingsApi]);

  return {
    value,
    onChangeValue: (next) =>
      setValue((prev) => ({ ...prev, value: next, error: null })),
    onSave: () => void saveResumePolicyAction(settingsApi, value, setValue),
  };
}
