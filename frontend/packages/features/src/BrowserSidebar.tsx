import { Box, Divider, NavLink, Stack, Text } from "@mantine/core";
import type { FsEntry, SidebarItem } from "./fileBrowserShared.ts";

type BrowserSidebarProps = {
  roots: FsEntry[];
  sidebarItems: SidebarItem[];
  currentPath: string;
  onNavigate: (path: string) => void;
};

/**
 * Drive and quick-access sidebar for the file browser.
 */
export function BrowserSidebar({
  roots,
  sidebarItems,
  currentPath,
  onNavigate,
}: BrowserSidebarProps) {
  return (
    <Box
      w={160}
      style={{
        borderRight: "1px solid var(--bl-border)",
        backgroundColor: "var(--bl-surface)",
        overflowY: "auto",
        flexShrink: 0,
      }}
      py="xs"
      px={4}
    >
      <Stack gap={2}>
        <Text
          size="xs"
          fw={700}
          tt="uppercase"
          c="var(--bl-text-muted)"
          px="xs"
          pt={4}
        >
          Drives
        </Text>
        {roots.map((r) => (
          <NavLink
            key={r.fullPath}
            label={r.name}
            leftSection={<span>💽</span>}
            active={currentPath.startsWith(r.fullPath)}
            onClick={() => onNavigate(r.fullPath)}
            variant="subtle"
            color="brand"
          />
        ))}
        <Divider my={6} color="var(--bl-border)" />
        <Text
          size="xs"
          fw={700}
          tt="uppercase"
          c="var(--bl-text-muted)"
          px="xs"
        >
          Quick Access
        </Text>
        {sidebarItems.map((item) => (
          <NavLink
            key={item.path}
            label={item.label}
            leftSection={<span>{item.icon}</span>}
            active={currentPath === item.path}
            onClick={() => onNavigate(item.path)}
            variant="subtle"
            color="brand"
          />
        ))}
      </Stack>
    </Box>
  );
}
