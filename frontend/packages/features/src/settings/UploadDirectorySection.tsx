import { Button, Code, Group, Loader, Paper, Stack, Text } from "@mantine/core";
import { FolderOpen } from "lucide-react";
import type { UploadDirState } from "./types.ts";
import { StatusMessage } from "./StatusMessage.tsx";

type UploadDirectorySectionProps = {
  uploadDir: UploadDirState;
  onOpenFolder: () => void;
  onBrowse: () => void;
};

/**
 * Upload directory path display, open and browse actions.
 */
export function UploadDirectorySection({
  uploadDir,
  onOpenFolder,
  onBrowse,
}: UploadDirectorySectionProps) {
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
          <FolderOpen size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Upload Directory
          </Text>
        </Group>

        <Button variant="light" color="brand" onClick={onOpenFolder} w="fit-content">
          Open
        </Button>

        <Stack gap="sm">
          <Group gap="xs" wrap="nowrap">
            <Code
              style={{
                flex: 1,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                backgroundColor: "var(--bl-background)",
                color: "var(--bl-text-primary)",
              }}
            >
              {uploadDir.current || "—"}
            </Code>
            <Button
              variant="light"
              color="brand"
              leftSection={<FolderOpen size={14} />}
              onClick={onBrowse}
            >
              Browse
            </Button>
          </Group>

          {uploadDir.save === "saving" ? (
            <Group gap="xs">
              <Loader size={16} color="brand" type="dots" />
              <Text size="sm" c="var(--bl-primary-text)">
                Saving...
              </Text>
            </Group>
          ) : null}
          {uploadDir.save === "success" ? (
            <StatusMessage type="success" message="Upload directory updated." />
          ) : null}
          {uploadDir.error ? (
            <StatusMessage type="error" message={uploadDir.error} />
          ) : null}
        </Stack>
      </Stack>
    </Paper>
  );
}
