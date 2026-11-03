import { Alert, Button, Group, Paper, Stack, Text, TextInput } from "@mantine/core";
import { NEXUS_CERTIFICATE_LABEL } from "@beamlink/core";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";
import { FingerprintText } from "./FingerprintText.tsx";
import type { NexusTlsPinControls } from "./useNexusTlsPin.ts";

type NexusTlsPinSectionProps = {
  controls: NexusTlsPinControls;
};

/**
 * Minimal first-contact confirm for the nexus TLS pin.
 */
export function NexusTlsPinSection({ controls }: NexusTlsPinSectionProps) {
  const {
    draft,
    setDraft,
    status,
    pendingFingerprint,
    error,
    correlationId,
    busy,
    probe,
    confirm,
    cancel,
    rePin,
  } = controls;

  const autoConfirm = Boolean(status.suggestedUrl) && !status.pinSet;

  return (
    <Paper
      withBorder
      p="lg"
      radius="md"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Stack gap="sm">
        <Text
          size="sm"
          fw={600}
          tt="uppercase"
          c="var(--bl-primary-text)"
          style={{ letterSpacing: "0.08em" }}
        >
          Nexus TLS
        </Text>
        <TextInput
          label="Nexus address"
          value={draft}
          onChange={(e) => setDraft(e.currentTarget.value)}
          placeholder="https://192.168.1.10:7473"
        />
        {status.suggestedUrl ? (
          <Text size="sm" c="var(--bl-text-secondary)">
            Suggested: {status.suggestedUrl}
          </Text>
        ) : null}
        {status.pinSet ? (
          <Text size="sm" c="var(--bl-text-secondary)">
            Pin confirmed for {status.url}
          </Text>
        ) : (
          <Text size="sm" c="var(--bl-text-secondary)">
            Connect, compare the fingerprint on the nexus screen, then confirm.
          </Text>
        )}
        {pendingFingerprint ? (
          <FingerprintText
            label={NEXUS_CERTIFICATE_LABEL}
            value={pendingFingerprint}
          />
        ) : null}
        <Group gap="sm">
          {autoConfirm ? (
            <Button
              loading={busy}
              disabled={!pendingFingerprint}
              onClick={() => void confirm()}
            >
              Confirm
            </Button>
          ) : (
            <>
              <Button loading={busy} onClick={() => void probe()}>
                Connect
              </Button>
              <Button
                loading={busy}
                disabled={!pendingFingerprint}
                onClick={() => void confirm()}
              >
                Confirm
              </Button>
              <Button variant="default" disabled={busy} onClick={() => void cancel()}>
                Cancel
              </Button>
              {status.pinSet ? (
                <Button variant="default" loading={busy} onClick={() => void rePin()}>
                  Re-pin
                </Button>
              ) : null}
            </>
          )}
        </Group>
        {error ? (
          <Alert color="danger" variant="light">
            <ErrorWithRef message={error} correlationId={correlationId} />
          </Alert>
        ) : null}
      </Stack>
    </Paper>
  );
}
