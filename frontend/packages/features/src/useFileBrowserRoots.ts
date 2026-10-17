import { useEffect, useState, type Dispatch, type SetStateAction } from "react";
import { usePlatform } from "@beamlink/platform";
import {
  getSidebarItems,
  type FsEntry,
  type SidebarItem,
} from "./fileBrowserShared.ts";

type Platform = ReturnType<typeof usePlatform>;

/**
 * Loads filesystem roots and sidebar shortcuts once.
 */
export function useFileBrowserRoots(
  platform: Platform,
  setError: Dispatch<SetStateAction<string | null>>,
) {
  const [roots, setRoots] = useState<FsEntry[]>([]);
  const [sidebarItems, setSidebarItems] = useState<SidebarItem[]>([]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const nextRoots = await platform.getRoots();
        const nextSidebar = await getSidebarItems(platform);
        if (!cancelled) {
          setRoots(nextRoots);
          setSidebarItems(nextSidebar);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setError((e as { message?: string }).message ?? "Cannot read directory");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [platform, setError]);

  return { roots, sidebarItems };
}
