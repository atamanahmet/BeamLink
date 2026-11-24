import { useEffect, useState } from "react";
import type { ResumePolicyMode, SettingsApi } from "@beamlink/core";
import { FirstRunDialog } from "./FirstRunDialog.tsx";
import { useAutostart } from "./useAutostart.ts";

export type FirstRunHostProps = {
  settingsApi: SettingsApi;
  /** Nexus always asks resume policy. Peer does not. */
  includeResumePolicy: boolean;
};

/**
 * Shows the first-run dialog when the flag is false and a question applies.
 */
export function FirstRunHost({
  settingsApi,
  includeResumePolicy,
}: FirstRunHostProps) {
  const autostart = useAutostart();
  const [completed, setCompleted] = useState<boolean | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [resumeReady, setResumeReady] = useState(!includeResumePolicy);
  const [liveResumePolicy, setLiveResumePolicy] =
    useState<ResumePolicyMode>("OWNER_ONLY");

  useEffect(() => {
    let cancelled = false;
    void settingsApi
      .getFirstRunCompleted()
      .then((data) => {
        if (!cancelled) {
          setCompleted(data.value);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCompleted(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [settingsApi]);

  useEffect(() => {
    if (!includeResumePolicy) {
      return;
    }
    let cancelled = false;
    void settingsApi
      .getResumePolicy()
      .then((data) => {
        if (!cancelled) {
          setLiveResumePolicy(data.resumePolicy);
          setResumeReady(true);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setResumeReady(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [includeResumePolicy, settingsApi]);

  const showAutostart = autostart.capability && autostart.supported;
  const questionApplies = showAutostart || includeResumePolicy;
  const open =
    !dismissed &&
    completed === false &&
    autostart.ready &&
    resumeReady &&
    questionApplies;

  if (!open) {
    return null;
  }

  return (
    <FirstRunDialog
      open={open}
      settingsApi={settingsApi}
      autostart={autostart}
      showAutostart={showAutostart}
      showResumePolicy={includeResumePolicy}
      initialAutostartEnabled={autostart.enabled}
      initialResumePolicy={liveResumePolicy}
      onDone={() => {
        setCompleted(true);
        setDismissed(true);
      }}
    />
  );
}
