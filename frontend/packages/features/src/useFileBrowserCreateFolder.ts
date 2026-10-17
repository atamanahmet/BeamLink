import { useCallback, useState } from "react";
import { usePlatform } from "@beamlink/platform";
import { createFolderUnder } from "./fileBrowserCreate.ts";

type Platform = ReturnType<typeof usePlatform>;

type UseFileBrowserCreateFolderArgs = {
  platform: Platform;
  currentPath: string;
  sep: string;
  reloadCurrent: () => void;
};

/**
 * Inline new-folder create flow for the current directory.
 */
export function useFileBrowserCreateFolder({
  platform,
  currentPath,
  sep,
  reloadCurrent,
}: UseFileBrowserCreateFolderArgs) {
  const [creatingFolder, setCreatingFolder] = useState(false);
  const [newFolderError, setNewFolderError] = useState<string | null>(null);

  const reset = useCallback(() => {
    setCreatingFolder(false);
    setNewFolderError(null);
  }, []);

  const handleCreateFolder = () => {
    if (!currentPath) return;
    setCreatingFolder(true);
    setNewFolderError(null);
  };

  const commitNewFolder = async (name: string) => {
    const result = await createFolderUnder(platform, currentPath, name, sep);
    if (!result.ok) {
      setNewFolderError(result.error);
      return;
    }
    setCreatingFolder(false);
    setNewFolderError(null);
    reloadCurrent();
  };

  const cancelNewFolder = () => {
    setCreatingFolder(false);
    setNewFolderError(null);
  };

  return {
    creatingFolder,
    newFolderError,
    reset,
    handleCreateFolder,
    commitNewFolder,
    cancelNewFolder,
  };
}
