import { Box, Button, Stack, Text, Title } from "@mantine/core";

/**
 * Fallback screen for route render errors. No stack trace.
 */
export function RouteErrorScreen() {
  return (
    <Box
      style={{
        minHeight: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 16,
        backgroundColor: "var(--bl-background)",
      }}
    >
      <Stack gap="md" maw={420} ta="center">
        <Title order={2} c="var(--bl-text-primary)">
          Something went wrong
        </Title>
        <Text c="var(--bl-text-secondary)">
          This page hit an unexpected error. Reload to try again.
        </Text>
        <Button onClick={() => window.location.reload()}>Reload</Button>
      </Stack>
    </Box>
  );
}
