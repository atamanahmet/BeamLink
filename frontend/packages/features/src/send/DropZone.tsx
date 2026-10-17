import type { DragEvent } from "react";
import { Button, Loader, Paper, Text } from "@mantine/core";
import { Upload } from "lucide-react";
import type { DropStatus } from "./getDropStatus.ts";

export type DropZoneProps = {
  status: DropStatus;
  onDragEnter: (e: DragEvent) => void;
  onDragLeave: (e: DragEvent) => void;
  onDragOver: (e: DragEvent) => void;
  onDrop: (e: DragEvent) => void | Promise<void>;
  onChooseFiles: () => void;
};

const DROP_TEXT: Record<DropStatus, string> = {
  sending: "Initiating transfer...",
  noPeer: "Select a destination agent above",
  offline: "Selected agent is offline",
  dragging: "Drop to send",
  idle: "Drag files or folders here",
};

export const DropZone = ({
  status,
  onDragEnter,
  onDragLeave,
  onDragOver,
  onDrop,
  onChooseFiles,
}: DropZoneProps) => {
  const canInteract = status === "dragging" || status === "idle";
  const dropActive = status === "dragging";

  return (
    <Paper
      withBorder
      p="xl"
      radius="md"
      onDragEnter={onDragEnter}
      onDragLeave={onDragLeave}
      onDragOver={onDragOver}
      onDrop={canInteract ? onDrop : undefined}
      style={{
        borderStyle: "dashed",
        borderWidth: 2,
        borderColor: dropActive
          ? "var(--bl-primary)"
          : "var(--bl-border-strong)",
        backgroundColor: dropActive
          ? "var(--bl-primary-subtle)"
          : "var(--bl-surface)",
        minHeight: 256,
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        gap: 24,
        textAlign: "center",
        opacity: canInteract ? 1 : 0.5,
        cursor: canInteract ? "pointer" : "not-allowed",
        transition: "border-color 150ms ease, background-color 150ms ease",
      }}
      styles={
        canInteract
          ? {
              root: {
                "&:hover": {
                  borderColor: "var(--bl-primary)",
                  backgroundColor: "var(--bl-primary-subtle)",
                },
              },
            }
          : undefined
      }
    >
      <Upload
        size={64}
        color={
          dropActive ? "var(--bl-primary-text)" : "var(--bl-border-strong)"
        }
      />

      <Text size="lg" c="var(--bl-text-primary)">
        {DROP_TEXT[status]}
      </Text>

      {canInteract ? <Button onClick={onChooseFiles}>Choose Files</Button> : null}

      {status === "sending" ? <Loader color="brand" type="dots" /> : null}
    </Paper>
  );
};
