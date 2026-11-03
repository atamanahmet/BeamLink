import { Button, Group, Stack, Text, TextInput } from "@mantine/core";
import {
  CONFIRM_LABEL,
  CONNECT_LABEL,
  FIND_NEXUS_TITLE,
  FINGERPRINT_MATCH_TEXT,
  NEXUS_CERTIFICATE_LABEL,
  VERIFY_FINGERPRINT_TITLE,
  WAITING_APPROVAL_BODY,
  WAITING_APPROVAL_TITLE,
} from "@beamlink/core";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";
import { FingerprintText } from "./FingerprintText.tsx";
import { connectStep } from "./connectStep.ts";
import type { NexusTlsPinControls } from "./useNexusTlsPin.ts";

export type ConnectStepsProps = {
  tlsPin: NexusTlsPinControls;
  deviceState: string | undefined;
  onRegister?: () => Promise<void>;
  registerAfterConfirm?: boolean;
  secondaryLabel: string;
  onSecondary: () => void;
  secondaryVariant?: "default" | "subtle";
};

/**
 * Find, verify, and waiting steps for Connect. Confirm is always a click.
 */
export function ConnectSteps({
  tlsPin,
  deviceState,
  onRegister,
  registerAfterConfirm = false,
  secondaryLabel,
  onSecondary,
  secondaryVariant = "default",
}: ConnectStepsProps) {
  const step = connectStep(
    deviceState,
    tlsPin.status.pinSet,
    tlsPin.pendingFingerprint,
  );

  /**
   * Finds Nexus, or registers again when the pin is already set.
   */
  const runConnect = async () => {
    if (tlsPin.status.pinSet) {
      await onRegister?.();
      return;
    }
    await tlsPin.probe();
  };

  /**
   * Stores the pin after an explicit Confirm click.
   */
  const runConfirm = async () => {
    await tlsPin.confirm();
    if (registerAfterConfirm) {
      await onRegister?.();
    }
  };

  return (
    <Stack gap="md">
      {step === "find" ? (
        <>
          <Text fw={600} c="var(--bl-text-primary)">
            {FIND_NEXUS_TITLE}
          </Text>
          {tlsPin.status.suggestedUrl ? (
            <Text size="sm" c="var(--bl-text-secondary)">
              {tlsPin.status.suggestedUrl}
            </Text>
          ) : (
            <TextInput
              value={tlsPin.draft}
              onChange={(e) => tlsPin.setDraft(e.currentTarget.value)}
              placeholder="https://192.168.1.10:7473"
            />
          )}
        </>
      ) : null}
      {step === "verify" ? (
        <>
          <Text fw={600} c="var(--bl-text-primary)">
            {VERIFY_FINGERPRINT_TITLE}
          </Text>
          <FingerprintText
            label={NEXUS_CERTIFICATE_LABEL}
            value={tlsPin.pendingFingerprint}
          />
          <Text size="sm" c="var(--bl-text-secondary)">
            {FINGERPRINT_MATCH_TEXT}
          </Text>
        </>
      ) : null}
      {step === "waiting" ? (
        <>
          <Text fw={600} c="var(--bl-text-primary)">
            {WAITING_APPROVAL_TITLE}
          </Text>
          <Text size="sm" c="var(--bl-text-secondary)">
            {WAITING_APPROVAL_BODY}
          </Text>
        </>
      ) : null}
      {tlsPin.error ? (
        <ErrorWithRef
          message={tlsPin.error}
          correlationId={tlsPin.correlationId}
        />
      ) : null}
      <Group justify="flex-end" gap="sm">
        <Button
          variant={secondaryVariant}
          onClick={onSecondary}
          disabled={tlsPin.busy}
        >
          {secondaryLabel}
        </Button>
        {step === "find" ? (
          <Button loading={tlsPin.busy} onClick={() => void runConnect()}>
            {CONNECT_LABEL}
          </Button>
        ) : null}
        {step === "verify" ? (
          <Button
            loading={tlsPin.busy}
            disabled={!tlsPin.pendingFingerprint}
            onClick={() => void runConfirm()}
          >
            {CONFIRM_LABEL}
          </Button>
        ) : null}
      </Group>
    </Stack>
  );
}
