import { useState } from "react";
import { Button, Group, Modal, Stack, Text } from "@mantine/core";
import {
  problemFromUnknown,
  type ResumePolicyMode,
  type SettingsApi,
} from "@beamlink/core";
import { AutostartSection } from "./AutostartSection.tsx";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";
import { ResumePolicySection } from "./ResumePolicySection.tsx";
import type { AutostartControls } from "./useAutostart.ts";

export type FirstRunDialogProps = {
  open: boolean;
  settingsApi: SettingsApi;
  autostart: AutostartControls;
  showAutostart: boolean;
  showResumePolicy: boolean;
  initialAutostartEnabled: boolean;
  initialResumePolicy: ResumePolicyMode;
  onDone: () => void;
};

/**
 * First-run questions for autostart and resume policy.
 */
export function FirstRunDialog({
  open,
  settingsApi,
  autostart,
  showAutostart,
  showResumePolicy,
  initialAutostartEnabled,
  initialResumePolicy,
  onDone,
}: FirstRunDialogProps) {
  const [bootEnabled, setBootEnabled] = useState(initialAutostartEnabled);
  const [resumePolicy, setResumePolicy] =
    useState<ResumePolicyMode>(initialResumePolicy);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [correlationId, setCorrelationId] = useState<string | null>(null);

  /**
   * Marks first run complete and keeps current defaults.
   */
  const skip = async () => {
    setSaving(true);
    setError(null);
    setCorrelationId(null);
    try {
      await settingsApi.putFirstRunCompleted(true);
      onDone();
    } catch (err) {
      const problem = problemFromUnknown(err);
      setError(problem.detail ?? "Could not save first-run choices.");
      setCorrelationId(problem.correlationId);
    } finally {
      setSaving(false);
    }
  };

  /**
   * Saves answers, then marks first run complete last.
   */
  const save = async () => {
    setSaving(true);
    setError(null);
    setCorrelationId(null);
    try {
      if (showAutostart) {
        await autostart.setEnabled(bootEnabled);
      }
      if (showResumePolicy) {
        await settingsApi.putResumePolicy(resumePolicy);
      }
      await settingsApi.putFirstRunCompleted(true);
      onDone();
    } catch (err) {
      const problem = problemFromUnknown(err);
      setError(problem.detail ?? "Could not save first-run choices.");
      setCorrelationId(problem.correlationId);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      opened={open}
      onClose={() => {
        if (!saving) {
          void skip();
        }
      }}
      title="Welcome"
      centered
      radius="md"
      closeOnClickOutside={false}
      closeOnEscape={!saving}
      withCloseButton={!saving}
      trapFocus
    >
      <Stack gap="md">
        <Text size="sm" c="var(--bl-text-secondary)">
          A few choices before you start. You can change these later in
          Settings.
        </Text>
        {showAutostart ? (
          <AutostartSection
            enabled={bootEnabled}
            onChange={setBootEnabled}
            disabled={saving}
          />
        ) : null}
        {showResumePolicy ? (
          <ResumePolicySection
            resumePolicy={{
              value: resumePolicy,
              save: "idle",
              error: null,
            }}
            onChangeValue={setResumePolicy}
            showSave={false}
          />
        ) : null}
        {error ? (
          <Text size="sm" c="var(--bl-danger-text)" role="alert">
            <ErrorWithRef message={error} correlationId={correlationId} />
          </Text>
        ) : null}
        <Group justify="flex-end">
          <Button loading={saving} onClick={() => void save()}>
            Continue
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
