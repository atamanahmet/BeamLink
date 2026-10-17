import { useComputedColorScheme } from "@mantine/core";
import { useMemo } from "react";
import { loadThemes } from "../theme/loadThemes.ts";
import type { ThemeTokens } from "../theme/types.ts";
import { composeThemeTokens } from "./themeSettings.ts";
import { useThemeSettings } from "./themeSettingsContext.tsx";

/**
 * Resolved theme tokens for the current theme id, primary seed, and color scheme.
 */
export function useThemeTokens(): ThemeTokens {
  const { themeId, primarySeed } = useThemeSettings();
  const colorScheme = useComputedColorScheme("dark");
  const themes = useMemo(() => loadThemes(), []);

  return useMemo(
    () => composeThemeTokens(themes, { themeId, primarySeed }, colorScheme),
    [themes, themeId, primarySeed, colorScheme],
  );
}
