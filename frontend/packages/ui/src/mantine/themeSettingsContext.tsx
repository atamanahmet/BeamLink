import { createContext, useContext } from "react";

export type ThemeSettingsContextValue = {
  themeId: string;
  setThemeId: (themeId: string) => void;
  primarySeed: string | undefined;
  setPrimarySeed: (seed: string) => void;
};

export const ThemeSettingsContext =
  createContext<ThemeSettingsContextValue | null>(null);

/**
 * Reads theme settings from the nearest ThemeController.
 */
export function useThemeSettings(): ThemeSettingsContextValue {
  const value = useContext(ThemeSettingsContext);
  if (!value) {
    throw new Error("useThemeSettings must be used within ThemeController");
  }
  return value;
}
