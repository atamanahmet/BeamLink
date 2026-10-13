import { usePlatform } from "@beamlink/platform";
import { ThemeController } from "@beamlink/ui";
import type { ReactNode } from "react";

/**
 * Wires ThemeController colors to Platform.setWindowTheme.
 */
export function ThemeWithPlatform({ children }: { children: ReactNode }) {
  const platform = usePlatform();

  /**
   * Forwards applied theme colors to the Electron window when supported.
   */
  function onThemeApplied(colors: {
    background: string;
    foreground: string;
  }): void {
    void platform.setWindowTheme?.(colors);
  }

  return (
    <ThemeController onThemeApplied={onThemeApplied}>
      {children}
    </ThemeController>
  );
}
