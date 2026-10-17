import {
  Button,
  Loader,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Group,
} from "@mantine/core";
import { KeyRound } from "lucide-react";
import type { CredentialsState } from "./types.ts";
import { StatusMessage } from "./StatusMessage.tsx";

type CredentialsSectionProps = {
  credentials: CredentialsState;
  onChange: (patch: Partial<CredentialsState>) => void;
  onSubmit: () => void;
};

/**
 * Change credentials form fields and update action.
 */
export function CredentialsSection({
  credentials,
  onChange,
  onSubmit,
}: CredentialsSectionProps) {
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
            Change Credentials
          </Text>
        </Group>

        <Text size="xs" c="var(--bl-text-muted)">
          After saving, you will be signed out and prompted to log in with your
          new credentials.
        </Text>

        <Stack gap="sm">
          <PasswordInput
            label="Current Password"
            value={credentials.currentPassword}
            onChange={(e) =>
              onChange({
                currentPassword: e.currentTarget.value,
                error: null,
              })
            }
            placeholder="Enter current password"
          />
          <TextInput
            label="New Username"
            value={credentials.newUsername}
            onChange={(e) =>
              onChange({
                newUsername: e.currentTarget.value,
                error: null,
              })
            }
            placeholder="Enter new username"
          />
          <PasswordInput
            label="New Password"
            value={credentials.newPassword}
            onChange={(e) =>
              onChange({
                newPassword: e.currentTarget.value,
                error: null,
              })
            }
            placeholder="At least 6 characters"
          />
          <PasswordInput
            label="Confirm New Password"
            value={credentials.confirmNewPassword}
            onChange={(e) =>
              onChange({
                confirmNewPassword: e.currentTarget.value,
                error: null,
              })
            }
            placeholder="Repeat new password"
          />

          {credentials.save === "success" ? (
            <StatusMessage
              type="success"
              message="Credentials updated. Signing you out..."
            />
          ) : null}
          {credentials.error ? (
            <StatusMessage type="error" message={credentials.error} />
          ) : null}

          <Button
            onClick={onSubmit}
            disabled={credentials.save === "saving"}
            w="fit-content"
            leftSection={
              credentials.save === "saving" ? (
                <Loader size={16} color="white" type="dots" />
              ) : undefined
            }
          >
            {credentials.save === "saving"
              ? "Saving..."
              : "Update Credentials"}
          </Button>
        </Stack>
      </Stack>
    </Paper>
  );
}
