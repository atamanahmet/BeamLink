import { useCallback, useState } from "react";
import { usePlatform } from "@beamlink/platform";
import { buildSegmentPath, pathToSegments } from "./fileBrowserPath.ts";
import { type FsEntry } from "./fileBrowserShared.ts";

type Platform = ReturnType<typeof usePlatform>;

type UseFileBrowserNavigationArgs = {
  platform: Platform;
  onBeforeLoad?: () => void;
};

/**
 * Directory path, listing, history and breadcrumb navigation.
 */
export function useFileBrowserNavigation({
  platform,
  onBeforeLoad,
}: UseFileBrowserNavigationArgs) {
  const [currentPath, setCurrentPath] = useState("");
  const [entries, setEntries] = useState<FsEntry[]>([]);
  const [history, setHistory] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDirectory = useCallback(
    async (dirPath: string, pushHistory = true) => {
      setLoading(true);
      setError(null);
      onBeforeLoad?.();
      try {
        const result: FsEntry[] = await platform.readDir(dirPath);
        const sorted = [...result].sort((a, b) => {
          if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
          return a.name.localeCompare(b.name);
        });
        if (pushHistory) setHistory((h) => [...h, currentPath]);
        setEntries(sorted);
        setCurrentPath(dirPath);
      } catch (e: unknown) {
        setError((e as { message?: string }).message ?? "Cannot read directory");
      } finally {
        setLoading(false);
      }
    },
    [currentPath, onBeforeLoad, platform],
  );

  const goBack = () => {
    if (history.length === 0) return;
    const prev = history[history.length - 1];
    setHistory((h) => h.slice(0, -1));
    if (prev === "") {
      setCurrentPath("");
      setEntries([]);
    } else {
      void loadDirectory(prev, false);
    }
  };

  const navigateInto = (entry: FsEntry) => {
    if (!entry.isDirectory || entry.locked) return;
    void loadDirectory(entry.fullPath);
  };

  const pathSegments = pathToSegments(currentPath);

  return {
    currentPath,
    entries,
    history,
    loading,
    error,
    setError,
    pathSegments,
    loadDirectory,
    goBack,
    navigateInto,
    buildSegmentPath: (index: number) =>
      buildSegmentPath(currentPath, pathSegments, index),
    setCurrentPath,
    setEntries,
    setHistory,
  };
}
