import { ActionIcon, Anchor, Button, Group, Text, Tooltip } from "@mantine/core";
import { Check, ChevronLeft, FolderPlus } from "lucide-react";

export type BrowserToolbarNav = {
  canGoBack: boolean;
  onBack: () => void;
  onGoPc: () => void;
  pathSegments: string[];
  onSegmentClick: (index: number) => void;
  currentPath: string;
};

export type BrowserToolbarChrome = {
  creatingFolder: boolean;
  onCreateFolder: () => void;
  mode: "multi" | "folder";
  selectedCount: number;
  showSelectedPanel: boolean;
  onToggleSelectedPanel: () => void;
};

type BrowserToolbarProps = {
  nav: BrowserToolbarNav;
  chrome: BrowserToolbarChrome;
};

/**
 * Back control, breadcrumbs, new folder and selection badge.
 */
export function BrowserToolbar({ nav, chrome }: BrowserToolbarProps) {
  const {
    canGoBack,
    onBack,
    onGoPc,
    pathSegments,
    onSegmentClick,
    currentPath,
  } = nav;
  const {
    creatingFolder,
    onCreateFolder,
    mode,
    selectedCount,
    showSelectedPanel,
    onToggleSelectedPanel,
  } = chrome;

  return (
    <Group
      gap="xs"
      px="sm"
      py={6}
      wrap="nowrap"
      style={{
        borderBottom: "1px solid var(--bl-border)",
        backgroundColor: "var(--bl-surface)",
        minHeight: 36,
      }}
    >
      <ActionIcon
        variant="subtle"
        color="brand"
        onClick={onBack}
        disabled={!canGoBack}
        aria-label="Back"
      >
        <ChevronLeft size={18} />
      </ActionIcon>

      <Group gap={4} style={{ flex: 1, overflow: "hidden" }} wrap="nowrap">
        <Anchor
          component="button"
          type="button"
          size="xs"
          onClick={onGoPc}
          c="var(--bl-primary-text)"
        >
          PC
        </Anchor>
        {pathSegments.map((seg, i) => (
          <Group key={i} gap={4} wrap="nowrap" style={{ minWidth: 0 }}>
            <Text size="xs" c="var(--bl-text-muted)">
              ›
            </Text>
            <Anchor
              component="button"
              type="button"
              size="xs"
              onClick={() => onSegmentClick(i)}
              c="var(--bl-primary-text)"
              style={{
                maxWidth: 150,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {seg}
            </Anchor>
          </Group>
        ))}
      </Group>

      {currentPath ? (
        <Tooltip label="New folder">
          <Button
            size="compact-xs"
            variant="light"
            color="brand"
            leftSection={<FolderPlus size={14} />}
            onClick={onCreateFolder}
            disabled={creatingFolder}
          >
            New Folder
          </Button>
        </Tooltip>
      ) : null}

      {mode === "multi" && selectedCount > 0 ? (
        <Button
          size="compact-xs"
          variant={showSelectedPanel ? "filled" : "light"}
          color="brand"
          leftSection={<Check size={12} />}
          onClick={onToggleSelectedPanel}
        >
          {selectedCount} selected
        </Button>
      ) : null}
    </Group>
  );
}
