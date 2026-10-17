import { useState } from "react";
import { browserConfirmLabel } from "./fileBrowserPath.ts";
import { nextSelection, withoutPath } from "./fileBrowserSelection.ts";
import { type FsEntry } from "./fileBrowserShared.ts";

type UseFileBrowserSelectionArgs = {
  mode: "multi" | "folder";
  currentPath: string;
  onConfirm: (paths: string[]) => void;
  onClose: () => void;
};

/**
 * File and folder selection plus confirm and clear.
 */
export function useFileBrowserSelection({
  mode,
  currentPath,
  onConfirm,
  onClose,
}: UseFileBrowserSelectionArgs) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [showSelectedPanel, setShowSelectedPanel] = useState(false);

  const toggleSelect = (entry: FsEntry) => {
    if (entry.locked) return;
    if (mode === "folder" && !entry.isDirectory) return;
    setSelected((prev) => nextSelection(prev, entry, mode));
  };

  const unselect = (fullPath: string) => {
    setSelected((prev) => withoutPath(prev, fullPath));
  };

  const clearAll = () => {
    setSelected(new Set());
    setShowSelectedPanel(false);
  };

  const dropFromSelection = (fullPath: string) => {
    setSelected((prev) => withoutPath(prev, fullPath));
  };

  const handleConfirm = () => {
    if (mode === "folder") {
      const target = selected.size > 0 ? [...selected][0] : currentPath;
      if (!target) return;
      onConfirm([target]);
      onClose();
      return;
    }
    if (selected.size === 0) return;
    onConfirm([...selected]);
    onClose();
  };

  const canConfirm =
    mode === "folder" ? selected.size > 0 || !!currentPath : selected.size > 0;

  return {
    selected,
    showSelectedPanel,
    setShowSelectedPanel,
    toggleSelect,
    unselect,
    clearAll,
    dropFromSelection,
    handleConfirm,
    canConfirm,
    confirmLabel: browserConfirmLabel(mode, selected.size, currentPath),
  };
}
