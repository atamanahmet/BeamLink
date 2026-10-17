import { useState } from "react";
import {
  Alert,
  Button,
  PasswordInput,
  Stack,
  TextInput,
} from "@mantine/core";
import { AuthCard } from "@beamlink/ui";
import axios from "axios";
import { login } from "../api/authApi";

/**
 * Maps a login failure to the inline error message.
 */
function loginErrorMessage(err: unknown): string {
  if (!axios.isAxiosError(err) || !err.response) {
    return "Cannot reach the server";
  }
  if (err.response.status === 401 || err.response.status === 403) {
    return "Wrong username or password";
  }
  return "Something went wrong, try again";
}

export const Login = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  /**
   * Submits credentials and shows an inline error on failure.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(loginErrorMessage(err));
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
          />
          <PasswordInput
            label="Password"
            placeholder="Enter password"
            value={password}
            onChange={(e) => setPassword(e.currentTarget.value)}
            autoComplete="current-password"
            required
            disabled={loading}
          />
          {error ? (
            <Alert color="danger" variant="light">
              {error}
            </Alert>
          ) : null}
          <Button type="submit" fullWidth loading={loading}>
            Access Nexus
          </Button>
        </Stack>
      </form>
    </AuthCard>
  );
};
