import { Modal } from "@mantine/core";
import { FileBrowserBody } from "./FileBrowserBody.tsx";
import type { FileBrowserModalProps } from "./fileBrowserShared.ts";

export type { FileBrowserModalProps, FsEntry } from "./fileBrowserShared.ts";

/**
 * Modal shell around the file browser body.
 */
export function FileBrowserModal({
  isOpen,
  onClose,
  onConfirm,
  title = "Select Files or Folders",
  mode = "multi",
}: FileBrowserModalProps) {
  return (
    <Modal
      opened={isOpen}
      onClose={onClose}
      title={title}
      size={860}
      centered
      padding={0}
      radius="md"
      styles={{
        content: {
          backgroundColor: "var(--bl-surface)",
          border: "1px solid var(--bl-border)",
          height: 580,
          display: "flex",
          flexDirection: "column",
        },
        header: {
          backgroundColor: "var(--bl-surface)",
          borderBottom: "1px solid var(--bl-border)",
          minHeight: 44,
        },
        title: {
          color: "var(--bl-text-primary)",
          fontWeight: 600,
          fontSize: 13,
        },
        body: {
          flex: 1,
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
          padding: 0,
          backgroundColor: "var(--bl-background)",
        },
      }}
    >
      {isOpen ? (
        <FileBrowserBody
          mode={mode}
          onConfirm={onConfirm}
          onClose={onClose}
        />
      ) : null}
    </Modal>
  );
}
