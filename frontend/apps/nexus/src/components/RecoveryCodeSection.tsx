import { useEffect, useState } from "react";
import {
  Button,
  Loader,
  Paper,
  PasswordInput,
  Stack,
  Text,
  Group,
} from "@mantine/core";
import { KeyRound } from "lucide-react";
import { ConfirmDialog } from "@beamlink/features";
import { problemFromUnknown } from "@beamlink/core";
import { fetchMe, regenerateRecoveryCode } from "../api/authApi";
import { recoveryCopy } from "../recoveryCopy.ts";
import { RecoveryCodeReveal } from "./RecoveryCodeReveal.tsx";

/**
 * Settings recovery code status, regenerate, and one-time reveal.
 */
export function RecoveryCodeSection() {
  const [hasCode, setHasCode] = useState<boolean | null>(null);
  const [password, setPassword] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [issued, setIssued] = useState<string | null>(null);

  useEffect(() => {
    void fetchMe()
      .then((me) => setHasCode(me.hasRecoveryCode))
      .catch(() => setHasCode(false));
  }, []);

  const runRegenerate = async () => {
    setPending(true);
    setError(null);
    try {
      const code = await regenerateRecoveryCode(password);
      setIssued(code);
      setHasCode(true);
      setPassword("");
      setConfirmOpen(false);
    } catch (err) {
      setError(problemFromUnknown(err).detail ?? "Could not regenerate.");
    } finally {
      setPending(false);
    }
  };

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
      <Stack gap="md">
        <Group gap="xs">
          <KeyRound size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            {recoveryCopy.recoveryLabel}
          </Text>
        </Group>
        {hasCode === false ? (
          <Text size="xs" c="var(--bl-text-muted)">
            {recoveryCopy.noCodeYet}
          </Text>
        ) : null}
        {issued ? (
          <RecoveryCodeReveal code={issued} warning={recoveryCopy.setupHint} />
        ) : null}
        <PasswordInput
          label={recoveryCopy.currentPassword}
          value={password}
          onChange={(e) => setPassword(e.currentTarget.value)}
          autoComplete="current-password"
        />
        {error ? (
          <Text size="sm" c="var(--bl-danger-text)" role="alert">
            {error}
          </Text>
        ) : null}
        <Button
          w="fit-content"
          disabled={!password}
          onClick={() => setConfirmOpen(true)}
          leftSection={
            pending ? <Loader size={16} color="white" type="dots" /> : undefined
          }
        >
          {recoveryCopy.regenerate}
        </Button>
      </Stack>
      <ConfirmDialog
        open={confirmOpen}
        title={recoveryCopy.regenerateTitle}
        body={recoveryCopy.regenerateBody}
        confirmLabel={recoveryCopy.regenerate}
        confirmColor="danger"
        pending={pending}
        error={error}
        onConfirm={() => void runRegenerate()}
        onCancel={() => {
          if (!pending) setConfirmOpen(false);
        }}
      />
    </Paper>
  );
}
