import { Stack, Text, Title } from "@mantine/core";

/**
 * Settings page title only.
 */
export function SettingsHeader() {
  return (
    <Stack gap={4}>
      <Title order={2} c="var(--bl-text-primary)">
        Settings
      </Title>
      <Text size="sm" c="var(--bl-text-secondary)">
        Manage storage, network, and diagnostics
      </Text>
    </Stack>
  );
}
