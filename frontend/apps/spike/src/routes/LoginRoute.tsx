import {
  Alert,
  Anchor,
  Button,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useState } from "react";
import { Link } from "react-router";

/**
 * Login screen mocked without a backend call.
 */
export function LoginRoute() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showError, setShowError] = useState(false);
  const [loading] = useState(false);

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Paper
        data-testid="login-card"
        withBorder
        p="xl"
        radius="md"
        w="100%"
        maw={420}
      >
        <Stack gap="md">
          <Title order={2} data-testid="login-title">
            BeamLink Agent
          </Title>
          <Text c="dimmed" size="sm" data-testid="login-subtitle">
            Network File Transfer Node
          </Text>

          <div data-testid="login-username-wrap">
            <TextInput
              label="Username"
              placeholder="Enter username"
              value={username}
              onChange={(event) => setUsername(event.currentTarget.value)}
              labelProps={{ "data-testid": "login-username-label" }}
            />
          </div>

          <div data-testid="login-password-wrap">
            <PasswordInput
              label="Password"
              placeholder="Enter password"
              value={password}
              onChange={(event) => setPassword(event.currentTarget.value)}
              labelProps={{ "data-testid": "login-password-label" }}
            />
          </div>

          {showError ? (
            <Alert
              data-testid="login-error"
              color="red"
              title="Sign in failed"
            >
              Invalid credentials
            </Alert>
          ) : null}

          <Button
            data-testid="login-submit"
            type="button"
            variant="filled"
            fullWidth
            loading={loading}
            onClick={(event) => event.preventDefault()}
          >
            {loading ? "Signing in..." : "Access Agent"}
          </Button>

          <Text ta="center" size="sm">
            First time?{" "}
            <Anchor
              data-testid="login-register-link"
              component={Link}
              to="/"
            >
              Create an account
            </Anchor>
          </Text>

          <Button
            data-testid="login-toggle-error"
            variant="light"
            onClick={() => setShowError((value) => !value)}
          >
            Toggle error
          </Button>
        </Stack>
      </Paper>
    </div>
  );
}
