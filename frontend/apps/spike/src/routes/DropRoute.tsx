import { Button, Paper, SimpleGrid, Stack, Text, Title } from "@mantine/core";
import {
  Dropzone,
  type DropzoneProps,
  type FileRejection,
  type FileWithPath,
} from "@mantine/dropzone";
import { useState, type DragEvent } from "react";
import { describeDroppedFiles } from "../drop/filePaths.ts";

type DropzoneEvent = Parameters<
  NonNullable<DropzoneProps["getFilesFromEvent"]>
>[0];

type ItemProbe = {
  kind: string;
  isDirectory: boolean | null;
};

/**
 * Formats one drop block for the log pre element.
 */
function formatDropBlock(
  label: string,
  files: ReturnType<typeof describeDroppedFiles>,
  extras?: { items?: ItemProbe[]; note?: string },
): string {
  const lines = [
    `--- ${new Date().toISOString()} ${label} ---`,
    `file count: ${files.length}`,
  ];
  if (extras?.note) {
    lines.push(extras.note);
  }
  if (extras?.items) {
    for (const item of extras.items) {
      lines.push(
        `item kind=${item.kind} isDirectory=${String(item.isDirectory)}`,
      );
    }
  }
  for (const file of files) {
    lines.push(
      `name=${file.name} size=${file.size} type=${file.type || "(empty)"} path=${file.path || "(empty)"}`,
    );
  }
  return lines.join("\n");
}

/**
 * Appends a block to a log, separated by a blank line.
 */
function appendLog(prev: string, block: string): string {
  return prev ? `${prev}\n\n${block}` : block;
}

/**
 * Reads DataTransferItem kind and directory flags before any await.
 */
function probeItems(list: DataTransferItemList | null): ItemProbe[] {
  if (!list) {
    return [];
  }
  const probes: ItemProbe[] = [];
  for (let i = 0; i < list.length; i += 1) {
    const item = list[i];
    if (!item) {
      continue;
    }
    const entry = item.webkitGetAsEntry?.() ?? null;
    probes.push({
      kind: item.kind,
      isDirectory: entry ? entry.isDirectory : null,
    });
  }
  return probes;
}

/**
 * Returns raw File list from a Dropzone event for folder drop trials.
 */
async function rawFilesFromEvent(event: DropzoneEvent): Promise<File[]> {
  if (Array.isArray(event)) {
    return Promise.all(event.map((handle) => handle.getFile()));
  }
  const transfer = "dataTransfer" in event ? event.dataTransfer : null;
  if (!transfer?.files) {
    return [];
  }
  return Array.from(transfer.files);
}

/**
 * Folder drop comparison page: plain handler vs Mantine Dropzone.
 */
export function Component() {
  const [plainLog, setPlainLog] = useState("");
  const [dropzoneLog, setDropzoneLog] = useState("");

  /**
   * Keeps Electron from opening the dropped path in the window.
   */
  function onPlainDragOver(event: DragEvent<HTMLDivElement>): void {
    event.preventDefault();
  }

  /**
   * Logs files and item directory flags from a native drop.
   */
  function onPlainDrop(event: DragEvent<HTMLDivElement>): void {
    event.preventDefault();
    const items = probeItems(event.dataTransfer.items);
    const fileList = Array.from(event.dataTransfer.files);
    const described = describeDroppedFiles(fileList);
    setPlainLog((prev) =>
      appendLog(prev, formatDropBlock("plain", described, { items })),
    );
  }

  /**
   * Logs files accepted by Mantine Dropzone.
   */
  function onDropzoneDrop(files: FileWithPath[]): void {
    const described = describeDroppedFiles(files);
    const note =
      files.length > 1
        ? "note: multiple files (folder may have been expanded)"
        : undefined;
    setDropzoneLog((prev) =>
      appendLog(prev, formatDropBlock("dropzone onDrop", described, { note })),
    );
  }

  /**
   * Logs Dropzone rejections when accept or size rules reject files.
   */
  function onDropzoneReject(rejections: FileRejection[]): void {
    const lines = [
      `--- ${new Date().toISOString()} dropzone onReject ---`,
      `rejection count: ${rejections.length}`,
    ];
    for (const rejection of rejections) {
      const codes = rejection.errors.map((err) => err.code).join(",");
      lines.push(
        `name=${rejection.file.name} size=${rejection.file.size} errors=${codes}`,
      );
    }
    setDropzoneLog((prev) => appendLog(prev, lines.join("\n")));
  }

  return (
    <Stack gap="md">
      <Title order={2}>Drop</Title>
      <Text size="sm">
        Drop files or folders into each area. Select log text to copy.
      </Text>

      <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
        <Paper
          withBorder
          p="md"
          data-testid="drop-plain-area"
          onDragOver={onPlainDragOver}
          onDrop={onPlainDrop}
          style={{ minHeight: 160 }}
        >
          <Stack gap="sm">
            <Text fw={600}>Plain handler</Text>
            <Text size="sm" c="dimmed">
              Native onDragOver / onDrop
            </Text>
            <Button
              data-testid="drop-plain-clear"
              variant="light"
              onClick={() => setPlainLog("")}
            >
              Clear
            </Button>
            <pre data-testid="drop-plain-log" style={{ whiteSpace: "pre-wrap" }}>
              {plainLog || "(empty)"}
            </pre>
          </Stack>
        </Paper>

        <Paper withBorder p="md" data-testid="drop-mantine-area">
          <Stack gap="sm">
            <Text fw={600}>Mantine Dropzone</Text>
            <Dropzone
              data-testid="drop-mantine-dropzone"
              onDrop={onDropzoneDrop}
              onReject={onDropzoneReject}
              getFilesFromEvent={rawFilesFromEvent}
            >
              <Text size="sm" c="dimmed" p="md">
                Drop files or folders here
              </Text>
            </Dropzone>
            <Button
              data-testid="drop-mantine-clear"
              variant="light"
              onClick={() => setDropzoneLog("")}
            >
              Clear
            </Button>
            <pre
              data-testid="drop-mantine-log"
              style={{ whiteSpace: "pre-wrap" }}
            >
              {dropzoneLog || "(empty)"}
            </pre>
          </Stack>
        </Paper>
      </SimpleGrid>
    </Stack>
  );
}
