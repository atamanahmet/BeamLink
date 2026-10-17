import { Box } from "@mantine/core";
import { BrowserFooter } from "./BrowserFooter.tsx";
import { BrowserFileGrid } from "./BrowserFileGrid.tsx";
import { BrowserSidebar } from "./BrowserSidebar.tsx";
import { BrowserToolbar } from "./BrowserToolbar.tsx";
import { SelectedPanel } from "./SelectedPanel.tsx";
import { useFileBrowser } from "./useFileBrowser.ts";

type FileBrowserBodyProps = {
  mode: "multi" | "folder";
  onConfirm: (paths: string[]) => void;
  onClose: () => void;
};

/**
 * Mounted browser content. Remount resets session state.
 */
export function FileBrowserBody({
  mode,
  onConfirm,
  onClose,
}: FileBrowserBodyProps) {
  const browser = useFileBrowser({ mode, onConfirm, onClose });

  return (
    <>
      <BrowserToolbar
        nav={{
          canGoBack: browser.history.length > 0,
          onBack: browser.goBack,
          onGoPc: () => {
            browser.setHistory((h) => [...h, browser.currentPath]);
            browser.setCurrentPath("");
            browser.setEntries([]);
          },
          pathSegments: browser.pathSegments,
          onSegmentClick: (i) =>
            void browser.loadDirectory(browser.buildSegmentPath(i)),
          currentPath: browser.currentPath,
        }}
        chrome={{
          creatingFolder: browser.creatingFolder,
          onCreateFolder: browser.handleCreateFolder,
          mode,
          selectedCount: browser.selected.size,
          showSelectedPanel: browser.showSelectedPanel,
          onToggleSelectedPanel: () =>
            browser.setShowSelectedPanel((v) => !v),
        }}
      />

      <Box
        style={{
          flex: 1,
          display: "flex",
          overflow: "hidden",
          position: "relative",
        }}
      >
        <BrowserSidebar
          roots={browser.roots}
          sidebarItems={browser.sidebarItems}
          currentPath={browser.currentPath}
          onNavigate={(path) => void browser.loadDirectory(path)}
        />

        <BrowserFileGrid
          view={{
            loading: browser.loading,
            error: browser.error,
            currentPath: browser.currentPath,
            roots: browser.roots,
            entries: browser.entries,
            selected: browser.selected,
            creatingFolder: browser.creatingFolder,
            newFolderError: browser.newFolderError,
          }}
          handlers={{
            onToggleSelect: browser.toggleSelect,
            onNavigateInto: browser.navigateInto,
            onOpenRoot: (entry) => void browser.loadDirectory(entry.fullPath),
            onCommitNewFolder: (name) => void browser.commitNewFolder(name),
            onCancelNewFolder: browser.cancelNewFolder,
          }}
        />

        {mode === "multi" && browser.showSelectedPanel ? (
          <SelectedPanel
            selected={browser.selected}
            entries={browser.entries}
            onUnselect={browser.unselect}
            onClearAll={browser.clearAll}
            onClose={() => browser.setShowSelectedPanel(false)}
          />
        ) : null}
      </Box>

      <BrowserFooter
        hint={browser.hint}
        confirmLabel={browser.confirmLabel}
        canConfirm={browser.canConfirm}
        onCancel={onClose}
        onConfirm={browser.handleConfirm}
      />
    </>
  );
}
