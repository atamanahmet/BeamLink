import { ActionIcon, Box, Button, Group, ScrollArea, Stack, Text } from "@mantine/core";
import { Trash2, X } from "lucide-react";
import { getFileIcon, type FsEntry } from "./fileBrowserShared.ts";

type SelectedPanelProps = {
  selected: Set<string>;
  entries: FsEntry[];
  onUnselect: (path: string) => void;
  onClearAll: () => void;
  onClose: () => void;
};

/**
 * Slide-in panel showing selected items with individual untick + clear all.
 */
export function SelectedPanel({
  selected,
  entries,
  onUnselect,
  onClearAll,
  onClose,
}: SelectedPanelProps) {
  const entryMap = new Map(entries.map((e) => [e.fullPath, e]));

  return (
    <Box
      pos="absolute"
      top={0}
      bottom={0}
      right={0}
      w={256}
      style={{
        backgroundColor: "var(--bl-surface)",
        borderLeft: "1px solid var(--bl-border)",
        display: "flex",
        flexDirection: "column",
        zIndex: 20,
      }}
    >
      <Group
        justify="space-between"
        px="sm"
        py="xs"
        style={{ borderBottom: "1px solid var(--bl-border)" }}
      >
        <Text size="xs" fw={700} tt="uppercase" c="var(--bl-primary-text)">
          Selected ({selected.size})
        </Text>
        <Group gap={4}>
          <Button
            size="compact-xs"
            variant="light"
            color="danger"
            leftSection={<Trash2 size={12} />}
            onClick={onClearAll}
          >
            Clear all
          </Button>
          <ActionIcon variant="subtle" color="brand" onClick={onClose} aria-label="Close">
            <X size={14} />
          </ActionIcon>
        </Group>
      </Group>

      <ScrollArea style={{ flex: 1 }}>
        <Stack gap={0} py={4}>
          {[...selected].map((fullPath) => {
            const entry = entryMap.get(fullPath);
            const name =
              entry?.name ?? fullPath.split(/[/\\]/).pop() ?? fullPath;
            const icon = entry?.isDirectory ? "📁" : getFileIcon(name);
            return (
              <Group
                key={fullPath}
                gap="xs"
                px="sm"
                py={6}
                wrap="nowrap"
                title={fullPath}
              >
                <Text size="sm" style={{ flexShrink: 0 }}>
                  {icon}
                </Text>
                <Text size="xs" c="var(--bl-text-primary)" truncate style={{ flex: 1 }}>
                  {name}
                </Text>
                <ActionIcon
                  size="xs"
                  variant="subtle"
                  color="danger"
                  onClick={() => onUnselect(fullPath)}
                  aria-label="Remove"
                >
                  <X size={12} />
                </ActionIcon>
              </Group>
            );
          })}
        </Stack>
      </ScrollArea>
    </Box>
  );
}
