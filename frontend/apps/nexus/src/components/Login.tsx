import { useState } from "react";
import { Link } from "react-router";
import {
  Alert,
  Anchor,
  Button,
  PasswordInput,
  Select,
  Stack,
  TextInput,
} from "@mantine/core";
import { parseRetryAfter, problemFromUnknown } from "@beamlink/core";
import { ErrorWithRef } from "@beamlink/features";
import { AuthCard } from "@beamlink/ui";
import axios from "axios";
import { login } from "../api/authApi";
import { recoveryCopy } from "../recoveryCopy.ts";
import type { SessionDays } from "../store/authStore";

/**
 * Maps a login failure to the inline error message.
 */
function loginErrorMessage(err: unknown): string {
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
    return "Wrong username or password";
  }
  return "Something went wrong, try again";
}

const SESSION_OPTIONS: { value: SessionDays; label: string }[] = [
  { value: "1", label: "1 day" },
  { value: "7", label: "7 days" },
  { value: "30", label: "30 days" },
  { value: "365", label: "365 days" },
];

export const Login = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [sessionDays, setSessionDays] = useState<SessionDays>("1");
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  /**
   * Submits credentials and shows an inline error on failure.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setCorrelationId(null);
    setLoading(true);
    try {
      await login(username.trim(), password, sessionDays);
    } catch (err) {
      setError(loginErrorMessage(err));
      setCorrelationId(problemFromUnknown(err).correlationId);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard
      brand="BeamLink Nexus"
      title="Sign in"
      subtitle="Central Command & Control"
    >
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
            size="sm"
          />
          <PasswordInput
            label="Password"
            placeholder="Enter password"
            value={password}
            onChange={(e) => setPassword(e.currentTarget.value)}
            autoComplete="current-password"
            required
            disabled={loading}
            size="sm"
          />
          <Select
            label="Stay signed in"
            data={SESSION_OPTIONS}
            value={sessionDays}
            onChange={(value) => {
              if (
                value === "1" ||
                value === "7" ||
                value === "30" ||
                value === "365"
              ) {
                setSessionDays(value);
              }
            }}
            disabled={loading}
            allowDeselect={false}
            size="sm"
          />
          {error ? (
            <Alert color="danger" variant="light">
              <ErrorWithRef message={error} correlationId={correlationId} />
            </Alert>
          ) : null}
          <Button type="submit" fullWidth loading={loading}>
            Access Nexus
          </Button>
          <Anchor component={Link} to="/reset-password" size="sm">
            {recoveryCopy.forgotLink}
          </Anchor>
        </Stack>
      </form>
    </AuthCard>
  );
};
