import type { ReactNode } from "react";
import { Box, Paper, Stack, Text, Title } from "@mantine/core";
import { glassSurfaceStyle } from "./glass.ts";

export type AuthCardProps = {
  brand: string;
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
};

/**
 * Centered auth layout with a glass card. Layout only.
 */
export function AuthCard({
  brand,
  title,
  subtitle,
  children,
  footer,
}: AuthCardProps) {
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
      <Paper
        withBorder
        p="xl"
        radius="md"
        w="100%"
        maw={400}
        style={{
          ...glassSurfaceStyle(),
          borderColor: "var(--bl-border)",
        }}
      >
        <Stack gap="md">
          <Stack gap={4} ta="center">
            <Text fw={700} size="sm" c="var(--bl-primary-text)">
              {brand}
            </Text>
            <Title order={2} c="var(--bl-text-primary)">
              {title}
            </Title>
            {subtitle ? (
              <Text size="sm" c="var(--bl-text-secondary)">
                {subtitle}
              </Text>
            ) : null}
          </Stack>
          {children}
          {footer ?? null}
        </Stack>
      </Paper>
    </Box>
  );
}
