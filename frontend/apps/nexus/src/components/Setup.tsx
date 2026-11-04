import { useState } from "react";
import {
  Alert,
  Button,
  PasswordInput,
  Stack,
  TextInput,
} from "@mantine/core";
import { problemFromUnknown } from "@beamlink/core";
import { ErrorWithRef } from "@beamlink/features";
import { AuthCard } from "@beamlink/ui";
import axios from "axios";
import { login, setupAdmin } from "../api/authApi";
import { recoveryCopy } from "../recoveryCopy.ts";
import { RecoveryCodeReveal } from "./RecoveryCodeReveal.tsx";

/**
 * Maps a setup failure to the inline error message.
 */
function setupErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err) || !err.response) {
    return "Cannot reach the server";
  }
  if (err.response.status === 409) {
    return "Password already set. Sign in instead.";
  }
  if (err.response.status === 403) {
    return "Setup is only allowed from this machine.";
  }
  const problem = problemFromUnknown(err);
  if (problem.detail) {
    return problem.detail;
  }
  return "Something went wrong, try again";
}

/**
 * First-run admin username and password form.
 */
export const Setup = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [issuedCode, setIssuedCode] = useState<string | null>(null);
  const [pendingName, setPendingName] = useState("");
  const [pendingPassword, setPendingPassword] = useState("");

  /**
   * Creates the first password, then shows the recovery code once.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setCorrelationId(null);
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setLoading(true);
    try {
      const name = username.trim();
      const recoveryCode = await setupAdmin(name, password);
      setPendingName(name);
      setPendingPassword(password);
      setIssuedCode(recoveryCode);
    } catch (err) {
      setError(setupErrorMessage(err));
      setCorrelationId(problemFromUnknown(err).correlationId);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard
      brand="BeamLink Nexus"
      title="Create admin"
      subtitle="First-run password setup"
    >
      {issuedCode ? (
        <RecoveryCodeReveal
          code={issuedCode}
          warning={recoveryCopy.setupHint}
          requireSaved
          onContinue={() => {
            void login(pendingName, pendingPassword, "1");
          }}
        />
      ) : (
      <form onSubmit={handleSubmit}>
        <Stack gap="md">
          <TextInput
            label="Username"
            placeholder="Enter username"
            value={username}
            onChange={(e) => setUsername(e.currentTarget.value)}
            autoFocus
            autoComplete="username"
            required
            disabled={loading}
          />
          <PasswordInput
            label="Password"
            placeholder="At least 8 characters"
            value={password}
            onChange={(e) => setPassword(e.currentTarget.value)}
            autoComplete="new-password"
            required
            disabled={loading}
          />
          <PasswordInput
            label="Confirm password"
            placeholder="Repeat password"
            value={confirm}
            onChange={(e) => setConfirm(e.currentTarget.value)}
            autoComplete="new-password"
            required
            disabled={loading}
          />
          {error ? (
            <Alert color="danger" variant="light">
              <ErrorWithRef message={error} correlationId={correlationId} />
            </Alert>
          ) : null}
          <Button type="submit" fullWidth loading={loading}>
            Create password
          </Button>
        </Stack>
      </form>
      )}
    </AuthCard>
  );
};
