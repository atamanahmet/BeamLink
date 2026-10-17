import { Box, Text } from "@mantine/core";
import { DriveCard, FileCard, NewFolderCard } from "./BrowserFileCards.tsx";
import type { FsEntry } from "./fileBrowserShared.ts";

export type BrowserFileGridView = {
  loading: boolean;
  error: string | null;
  currentPath: string;
  roots: FsEntry[];
  entries: FsEntry[];
  selected: Set<string>;
  creatingFolder: boolean;
  newFolderError: string | null;
};

export type BrowserFileGridHandlers = {
  onToggleSelect: (entry: FsEntry) => void;
  onNavigateInto: (entry: FsEntry) => void;
  onOpenRoot: (entry: FsEntry) => void;
  onCommitNewFolder: (name: string) => void;
  onCancelNewFolder: () => void;
};

type BrowserFileGridProps = {
  view: BrowserFileGridView;
  handlers: BrowserFileGridHandlers;
};

/**
 * Loading, error, empty, drive and file grid content.
 */
export function BrowserFileGrid({ view, handlers }: BrowserFileGridProps) {
  const {
    loading,
    error,
    currentPath,
    roots,
    entries,
    selected,
    creatingFolder,
    newFolderError,
  } = view;
  const {
    onToggleSelect,
    onNavigateInto,
    onOpenRoot,
    onCommitNewFolder,
    onCancelNewFolder,
  } = handlers;

  if (loading) {
    return (
      <Box
        style={{
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Text size="sm" c="var(--bl-text-secondary)">
          Loading...
        </Text>
      </Box>
    );
  }

  if (error) {
    return (
      <Box
        style={{
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Text size="xs" ta="center" c="var(--bl-danger-text)">
          {error}
        </Text>
      </Box>
    );
  }

  if (currentPath === "") {
    return (
      <Box p="md" style={{ flex: 1, overflowY: "auto" }}>
        <Text
          size="xs"
          fw={700}
          tt="uppercase"
          c="var(--bl-text-muted)"
          mb="sm"
        >
          Devices and Drives
        </Text>
        <Box style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {roots.map((r) => (
            <DriveCard
              key={r.fullPath}
              entry={r}
              selected={selected.has(r.fullPath)}
              onClick={() => onToggleSelect(r)}
              onDoubleClick={() => onOpenRoot(r)}
            />
          ))}
        </Box>
      </Box>
    );
  }

  if (entries.length === 0 && !creatingFolder) {
    return (
      <Box
        style={{
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Text size="xs" c="var(--bl-text-muted)">
          Empty folder
        </Text>
      </Box>
    );
  }

  return (
    <Box p="sm" style={{ flex: 1, overflowY: "auto" }}>
      <Box
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(88px, 1fr))",
          gap: 2,
        }}
      >
        {creatingFolder ? (
          <NewFolderCard
            onConfirm={onCommitNewFolder}
            onCancel={onCancelNewFolder}
            error={newFolderError}
          />
        ) : null}
        {entries.map((entry) => (
          <FileCard
            key={entry.fullPath}
            entry={entry}
            selected={selected.has(entry.fullPath)}
            onClick={() => onToggleSelect(entry)}
            onDoubleClick={() => onNavigateInto(entry)}
          />
        ))}
      </Box>
    </Box>
  );
}
