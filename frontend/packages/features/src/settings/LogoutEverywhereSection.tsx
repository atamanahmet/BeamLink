import { Button, Paper, Stack, Text, Group } from "@mantine/core";
import { ShieldOff } from "lucide-react";

type LogoutEverywhereSectionProps = {
  onLogoutAll: () => void;
};

/**
 * Revokes every admin session on this Nexus.
 */
export function LogoutEverywhereSection({
  onLogoutAll,
}: LogoutEverywhereSectionProps) {
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
          <ShieldOff size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Sessions
          </Text>
        </Group>
        <Text size="xs" c="var(--bl-text-muted)">
          Sign out every device that has a saved admin token for this Nexus.
        </Text>
        <Button
          color="danger"
          variant="light"
          w="fit-content"
          onClick={onLogoutAll}
        >
          Log out everywhere
        </Button>
      </Stack>
    </Paper>
  );
}
