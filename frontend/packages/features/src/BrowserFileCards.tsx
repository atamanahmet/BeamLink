import { useEffect, useRef, useState } from "react";
import { Box, Stack, Text, TextInput, Tooltip } from "@mantine/core";
import { Check } from "lucide-react";
import { formatSize, getFileIcon, type FsEntry } from "./fileBrowserShared.ts";

/**
 * Drive tile on the root view.
 */
export function DriveCard({
  entry,
  selected,
  onClick,
  onDoubleClick,
}: {
  entry: FsEntry;
  selected: boolean;
  onClick: () => void;
  onDoubleClick: () => void;
}) {
  return (
    <Box
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      w={128}
      p="sm"
      style={{
        borderRadius: 8,
        border: `1px solid ${selected ? "var(--bl-primary)" : "var(--bl-border)"}`,
        backgroundColor: selected
          ? "var(--bl-primary-subtle)"
          : "var(--bl-surface)",
        cursor: "pointer",
        userSelect: "none",
        display: "flex",
        flexDirection: "column",
        gap: 6,
      }}
    >
      <Text size="xl">💽</Text>
      <Text size="xs" fw={600} truncate c="var(--bl-primary-text)">
        {entry.name}
      </Text>
    </Box>
  );
}

/**
 * Inline rename input rendered inside the file grid for new folder creation.
 */
export function NewFolderCard({
  onConfirm,
  onCancel,
  error,
}: {
  onConfirm: (name: string) => void;
  onCancel: () => void;
  error: string | null;
}) {
  const [name, setName] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const commit = () => {
    const trimmed = name.trim();
    if (trimmed) onConfirm(trimmed);
    else onCancel();
  };

  return (
    <Stack
      gap={4}
      align="center"
      p="xs"
      style={{
        borderRadius: 6,
        border: "1px solid var(--bl-primary)",
        backgroundColor: "var(--bl-primary-subtle)",
        userSelect: "none",
      }}
    >
      <Text size="xl" lh={1}>
        📁
      </Text>
      <TextInput
        ref={inputRef}
        value={name}
        onChange={(e) => setName(e.currentTarget.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") commit();
          if (e.key === "Escape") onCancel();
          e.stopPropagation();
        }}
        onBlur={commit}
        size="xs"
        styles={{ input: { textAlign: "center", fontFamily: "monospace" } }}
      />
      {error ? (
        <Text size="xs" c="var(--bl-danger-text)" ta="center">
          {error}
        </Text>
      ) : null}
    </Stack>
  );
}

/**
 * File or folder tile in the grid.
 */
export function FileCard({
  entry,
  selected,
  onClick,
  onDoubleClick,
}: {
  entry: FsEntry;
  selected: boolean;
  onClick: () => void;
  onDoubleClick: () => void;
}) {
  const icon = entry.isDirectory ? "📁" : getFileIcon(entry.name);
  const tipLines: string[] = [];
  if (entry.locked) tipLines.push("Locked by OS, cannot be sent.");
  if (entry.hidden) tipLines.push("Hidden file, can be sent.");
  const tip = tipLines.join(" ");

  const card = (
    <Box
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      pos="relative"
      px={4}
      py="xs"
      style={{
        borderRadius: 6,
        border: `1px solid ${
          entry.locked
            ? "var(--bl-danger)"
            : selected
              ? "var(--bl-primary)"
              : "transparent"
        }`,
        backgroundColor: entry.locked
          ? "var(--bl-danger-subtle)"
          : selected
            ? "var(--bl-primary-subtle)"
            : "transparent",
        cursor: entry.locked ? "not-allowed" : "pointer",
        userSelect: "none",
        opacity: entry.hidden ? 0.5 : 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
      }}
    >
      {selected && !entry.locked ? (
        <Box
          pos="absolute"
          top={4}
          left={4}
          w={14}
          h={14}
          style={{
            borderRadius: "50%",
            backgroundColor: "var(--bl-primary)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <Check size={8} color="var(--bl-primary-foreground)" strokeWidth={3} />
        </Box>
      ) : null}

      {entry.locked ? (
        <Text
          pos="absolute"
          top={4}
          right={4}
          size="xs"
          lh={1}
          c="var(--bl-danger-text)"
        >
          🔒
        </Text>
      ) : null}

      <Text size="xl" lh={1} mb={4}>
        {icon}
      </Text>

      <Text
        size="xs"
        ta="center"
        w="100%"
        truncate
        fs={entry.hidden ? "italic" : undefined}
        c={
          entry.locked
            ? "var(--bl-danger-text)"
            : selected
              ? "var(--bl-primary-text)"
              : "var(--bl-text-secondary)"
        }
      >
        {entry.name}
      </Text>

      {!entry.isDirectory && entry.size != null ? (
        <Text size="xs" c="var(--bl-text-muted)" mt={2}>
          {formatSize(entry.size)}
        </Text>
      ) : null}
    </Box>
  );

  if (tip) {
    return (
      <Tooltip label={tip} withArrow>
        {card}
      </Tooltip>
    );
  }

  return card;
}
