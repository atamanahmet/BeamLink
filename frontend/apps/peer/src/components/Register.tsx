import { useState } from "react";
import { useNavigate } from "react-router";
import {
  Alert,
  Anchor,
  Button,
  PasswordInput,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { AuthCard } from "@beamlink/ui";
import axios from "axios";
import { register as registerUser } from "../api/authApi";

/**
 * Maps a register failure to the inline error message.
 */
function registerErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err) || !err.response) {
    return "Cannot reach the server";
  }
  const apiError = err.response.data?.error;
  if (typeof apiError === "string" && apiError.length > 0) {
    return apiError;
  }
  return "Something went wrong, try again";
}

export const Register = () => {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  /**
   * Validates locally, then creates the account.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    if (password.length < 6) {
      setError("Password must be at least 6 characters.");
      return;
    }

    setLoading(true);
    try {
      await registerUser(username.trim(), password);
      navigate("/login", { replace: true });
    } catch (err) {
      setError(registerErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthCard
      brand="BeamLink Agent"
      title="Create an account"
      subtitle="Create your UI account to get started"
      footer={
        <Text ta="center" size="sm" c="var(--bl-text-secondary)">
          Already have an account?{" "}
          <Anchor
            component="button"
            type="button"
            onClick={() => navigate("/login")}
          >
            Sign in
          </Anchor>
        </Text>
      }
    >
      <form onSubmit={handleSubmit}>
        <Stack gap="md">
          <TextInput
            label="Username"
            placeholder="Choose a username"
            value={username}
            onChange={(e) => setUsername(e.currentTarget.value)}
            autoFocus
            autoComplete="username"
            required
            disabled={loading}
          />
          <PasswordInput
            label="Password"
            placeholder="At least 6 characters"
            value={password}
            onChange={(e) => setPassword(e.currentTarget.value)}
            autoComplete="new-password"
            required
            disabled={loading}
          />
          <PasswordInput
            label="Confirm Password"
            placeholder="Repeat your password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.currentTarget.value)}
            autoComplete="new-password"
            required
            disabled={loading}
          />
          {error ? (
            <Alert color="danger" variant="light">
              {error}
            </Alert>
          ) : null}
          <Button type="submit" fullWidth loading={loading}>
            Create Account
          </Button>
        </Stack>
      </form>
    </AuthCard>
  );
};
