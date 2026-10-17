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
  const navigate = useNavigate();

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
      brand="BeamLink Agent"
      title="Sign in"
      subtitle="Network File Transfer Node"
      footer={
        <Text ta="center" size="sm" c="var(--bl-text-secondary)">
          First time?{" "}
          <Anchor
            component="button"
            type="button"
            onClick={() => navigate("/register")}
          >
            Create an account
          </Anchor>
        </Text>
      }
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
            Access Agent
          </Button>
        </Stack>
      </form>
    </AuthCard>
  );
};
