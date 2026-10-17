import { useCallback, useEffect, useRef } from "react";
import { usePlatform } from "@beamlink/platform";
import { browserHint } from "./fileBrowserPath.ts";
import { type FsEntry } from "./fileBrowserShared.ts";
import { useFileBrowserCreateFolder } from "./useFileBrowserCreateFolder.ts";
import { useFileBrowserNavigation } from "./useFileBrowserNavigation.ts";
import { useFileBrowserRoots } from "./useFileBrowserRoots.ts";
import { useFileBrowserSelection } from "./useFileBrowserSelection.ts";

type UseFileBrowserArgs = {
  mode: "multi" | "folder";
  onConfirm: (paths: string[]) => void;
  onClose: () => void;
};

/**
 * File browser navigation, selection and folder create logic.
 */
export function useFileBrowser({
  mode,
  onConfirm,
  onClose,
}: UseFileBrowserArgs) {
  const platform = usePlatform();
  const sep = platform.env.sep;
  const resetCreateRef = useRef(() => {});
  const onBeforeLoad = useCallback(() => {
    resetCreateRef.current();
  }, []);

  const nav = useFileBrowserNavigation({ platform, onBeforeLoad });
  const { roots, sidebarItems } = useFileBrowserRoots(platform, nav.setError);
  const selection = useFileBrowserSelection({
    mode,
    currentPath: nav.currentPath,
    onConfirm,
    onClose,
  });
  const create = useFileBrowserCreateFolder({
    platform,
    currentPath: nav.currentPath,
    sep,
    reloadCurrent: () => {
      void nav.loadDirectory(nav.currentPath, false);
    },
  });

  useEffect(() => {
    resetCreateRef.current = create.reset;
  }, [create.reset]);

  const navigateInto = (entry: FsEntry) => {
    if (!entry.isDirectory || entry.locked) return;
    selection.dropFromSelection(entry.fullPath);
    void nav.loadDirectory(entry.fullPath);
  };

  return {
    roots,
    sidebarItems,
    currentPath: nav.currentPath,
    entries: nav.entries,
    selected: selection.selected,
    history: nav.history,
    loading: nav.loading,
    error: nav.error,
    showSelectedPanel: selection.showSelectedPanel,
    setShowSelectedPanel: selection.setShowSelectedPanel,
    creatingFolder: create.creatingFolder,
    newFolderError: create.newFolderError,
    pathSegments: nav.pathSegments,
    canConfirm: selection.canConfirm,
    confirmLabel: selection.confirmLabel,
    hint: browserHint(mode),
    loadDirectory: nav.loadDirectory,
    goBack: nav.goBack,
    toggleSelect: selection.toggleSelect,
    navigateInto,
    handleConfirm: selection.handleConfirm,
    handleCreateFolder: create.handleCreateFolder,
    commitNewFolder: create.commitNewFolder,
    cancelNewFolder: create.cancelNewFolder,
    unselect: selection.unselect,
    clearAll: selection.clearAll,
    buildSegmentPath: nav.buildSegmentPath,
    setCurrentPath: nav.setCurrentPath,
    setEntries: nav.setEntries,
    setHistory: nav.setHistory,
  };
}
