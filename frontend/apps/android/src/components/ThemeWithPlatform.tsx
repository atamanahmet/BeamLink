import { useEffect, type ReactNode } from "react";
import { useComputedColorScheme } from "@mantine/core";
import { ThemeController } from "@beamlink/ui";
import { Beamlink } from "../native/beamlinkPlugin.ts";

/**
 * Pushes in-app light or dark mode to native status bar icons.
 */
function StatusBarSync() {
  const scheme = useComputedColorScheme("dark");

  useEffect(() => {
    void Beamlink.setStatusBarLight({ light: scheme === "light" });
  }, [scheme]);

  return null;
}

/**
 * ThemeController wrapper. Android has no native window chrome theme hook.
 */
export function ThemeWithPlatform({ children }: { children: ReactNode }) {
  return (
    <ThemeController phone>
      <StatusBarSync />
      {children}
    </ThemeController>
  );
}
