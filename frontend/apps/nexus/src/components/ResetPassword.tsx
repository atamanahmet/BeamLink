import { useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  Alert,
  Anchor,
  Button,
  PasswordInput,
  Stack,
  TextInput,
} from "@mantine/core";
import { parseRetryAfter, problemFromUnknown } from "@beamlink/core";
import { ErrorWithRef, TypedConfirmDialog } from "@beamlink/features";
import { AuthCard } from "@beamlink/ui";
import axios from "axios";
import { eraseAll, resetPassword } from "../api/authApi";
import { recoveryCopy } from "../recoveryCopy.ts";
import { useAuthStore } from "../store/authStore";
import { RecoveryCodeReveal } from "./RecoveryCodeReveal.tsx";

/**
 * Maps a reset failure to the inline error message.
 */
function resetErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err) || !err.response) {
    return "Cannot reach the server";
  }
  if (err.response.status === 429) {
    const header = err.response.headers["retry-after"];
    const wait = parseRetryAfter(typeof header === "string" ? header : null);
    if (wait !== null) {
      const unit = wait === 1 ? "second" : "seconds";
      return `Too many attempts. Try again in ${wait} ${unit}.`;
    }
    return "Too many attempts. Try again in a minute.";
  }
  if (err.response.status === 401 || err.response.status === 403) {
    return "Wrong recovery code";
  }
  const problem = problemFromUnknown(err);
  if (problem.detail) {
    return problem.detail;
  }
  return "Something went wrong, try again";
}

export const ResetPassword = () => {
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [newCode, setNewCode] = useState<string | null>(null);
  const [eraseOpen, setEraseOpen] = useState(false);
  const [erasePending, setErasePending] = useState(false);
  const [eraseError, setEraseError] = useState<string | null>(null);
  const [eraseResult, setEraseResult] = useState<string | null>(null);

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
      const next = await resetPassword(code, password);
      setNewCode(next);
    } catch (err) {
      setError(resetErrorMessage(err));
      setCorrelationId(problemFromUnknown(err).correlationId);
    } finally {
      setLoading(false);
    }
  };

  const handleErase = async () => {
    setErasePending(true);
    setEraseError(null);
    try {
      await eraseAll();
      setEraseResult("All nexus data was erased.");
    } catch (err) {
      setEraseError(resetErrorMessage(err));
      setCorrelationId(problemFromUnknown(err).correlationId);
    } finally {
      setErasePending(false);
    }
  };

  const closeErase = () => {
    if (erasePending) return;
    const goSetup = eraseResult != null;
    setEraseOpen(false);
    setEraseError(null);
    setEraseResult(null);
    if (goSetup) {
      useAuthStore.getState().clearSession();
      useAuthStore.getState().setStatus("setup");
      navigate("/setup", { replace: true });
    }
  };

  return (
    <AuthCard
      brand="BeamLink Nexus"
      title={recoveryCopy.resetTitle}
      subtitle={recoveryCopy.resetWarning}
    >
      {newCode ? (
        <RecoveryCodeReveal
          code={newCode}
          requireSaved
          onContinue={() => navigate("/login", { replace: true })}
        />
      ) : (
        <form onSubmit={handleSubmit}>
          <Stack gap="md">
            <TextInput
              label={recoveryCopy.recoveryLabel}
              value={code}
              onChange={(e) => setCode(e.currentTarget.value)}
              autoFocus
              required
              disabled={loading}
              size="sm"
            />
            <PasswordInput
              label="Password"
              placeholder="At least 8 characters"
              value={password}
              onChange={(e) => setPassword(e.currentTarget.value)}
              autoComplete="new-password"
              required
              disabled={loading}
              size="sm"
            />
            <PasswordInput
              label="Confirm password"
              placeholder="Repeat password"
              value={confirm}
              onChange={(e) => setConfirm(e.currentTarget.value)}
              autoComplete="new-password"
              required
              disabled={loading}
              size="sm"
            />
            {error ? (
              <Alert color="danger" variant="light">
                <ErrorWithRef message={error} correlationId={correlationId} />
              </Alert>
            ) : null}
            <Button type="submit" fullWidth loading={loading}>
              Reset password
            </Button>
            <Anchor
              component="button"
              type="button"
              size="sm"
              onClick={() => setEraseOpen(true)}
            >
              {recoveryCopy.lostCodeLink}
            </Anchor>
            <Anchor component={Link} to="/login" size="sm">
              Back to sign in
            </Anchor>
          </Stack>
        </form>
      )}
      <TypedConfirmDialog
        open={eraseOpen}
        title={recoveryCopy.eraseTitle}
        body={recoveryCopy.eraseBody}
        confirmWord={recoveryCopy.eraseWord}
        confirmLabel={recoveryCopy.eraseTitle}
        pending={erasePending}
        error={eraseError}
        correlationId={correlationId}
        result={eraseResult}
        onConfirm={() => void handleErase()}
        onCancel={closeErase}
      />
    </AuthCard>
  );
};
