import { MantineProvider, useComputedColorScheme } from "@mantine/core";
import {
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  fontFamilyStack,
  resolveFont,
  resolveFontId,
} from "../theme/fonts.ts";
import { loadThemes } from "../theme/loadThemes.ts";
import type { ThemeFile } from "../theme/types.ts";
import { buildCssVariables, buildMantineTheme } from "./mantineTheme.ts";
import {
  buildBackgroundCache,
  composeThemeTokens,
  resolveThemeSeeds,
  resolveThemeSettings,
  sanitizePrimarySeed,
  serializeBackgroundCache,
  THEME_BG_STORAGE_KEY,
  THEME_STORAGE_KEY,
  type ThemeSettings,
} from "./themeSettings.ts";
import {
  ThemeSettingsContext,
  type ThemeSettingsContextValue,
} from "./themeSettingsContext.tsx";

type ThemeWindowColors = {
  background: string;
  foreground: string;
};

/**
 * Reads the theme JSON from localStorage, or null on failure.
 */
function readThemeStorage(): string | null {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY);
  } catch {
    return null;
  }
}

/**
 * Writes theme settings and background cache. Ignores storage failures.
 */
function persistThemeState(
  settings: ThemeSettings,
  themes: ReturnType<typeof loadThemes>,
): void {
  try {
    const payload =
      settings.primarySeed === undefined
        ? { themeId: settings.themeId, fontId: settings.fontId }
        : {
            themeId: settings.themeId,
            primarySeed: settings.primarySeed,
            fontId: settings.fontId,
          };
    localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Storage may be unavailable.
  }

  try {
    const seeds = resolveThemeSeeds(themes, settings);
    const cache = buildBackgroundCache(seeds);
    localStorage.setItem(
      THEME_BG_STORAGE_KEY,
      serializeBackgroundCache(cache),
    );
  } catch {
    // Storage may be unavailable.
  }
}

/**
 * Applies UI and mono font CSS variables on the document root.
 */
function applyFontVariables(fontId: string): void {
  const root = document.documentElement;
  const ui = resolveFont(fontId);
  const mono = resolveFont("system-mono");
  root.style.setProperty("--font-ui", fontFamilyStack(ui));
  root.style.setProperty("--font-mono", fontFamilyStack(mono));
}

/**
 * Pushes active page background and text color through onThemeApplied.
 */
function WindowThemeSync({
  themes,
  settings,
  onThemeApplied,
}: {
  themes: ThemeFile[];
  settings: ThemeSettings;
  onThemeApplied?: (colors: ThemeWindowColors) => void;
}) {
  const colorScheme = useComputedColorScheme("dark");
  useEffect(() => {
    const tokens = composeThemeTokens(themes, settings, colorScheme);
    onThemeApplied?.({
      background: tokens.background,
      foreground: tokens["text-primary"],
    });
  }, [themes, settings, colorScheme, onThemeApplied]);
  return null;
}

/**
 * Wraps MantineProvider with persisted theme settings and CSS variables.
 */
export function ThemeController({
  children,
  onThemeApplied,
}: {
  children: ReactNode;
  onThemeApplied?: (colors: ThemeWindowColors) => void;
}) {
  const themes = useMemo(() => loadThemes(), []);
  const [settings, setSettings] = useState<ThemeSettings>(() =>
    resolveThemeSettings(readThemeStorage(), loadThemes()),
  );

  const seeds = useMemo(
    () => resolveThemeSeeds(themes, settings),
    [themes, settings],
  );
  const theme = useMemo(() => buildMantineTheme(seeds), [seeds]);
  const cssVariablesResolver = useMemo(
    () => () => buildCssVariables(seeds),
    [seeds],
  );

  useEffect(() => {
    persistThemeState(settings, themes);
  }, [settings, themes]);

  useEffect(() => {
    applyFontVariables(settings.fontId);
  }, [settings.fontId]);

  const value = useMemo<ThemeSettingsContextValue>(
    () => ({
      themeId: settings.themeId,
      primarySeed: settings.primarySeed,
      setThemeId: (themeId) => {
        const next = resolveThemeSettings(
          JSON.stringify({
            themeId,
            primarySeed: settings.primarySeed,
            fontId: settings.fontId,
          }),
          themes,
        );
        setSettings(next);
      },
      setPrimarySeed: (seed) => {
        const primarySeed = sanitizePrimarySeed(seed);
        if (primarySeed === undefined) {
          return;
        }
        setSettings({
          themeId: settings.themeId,
          primarySeed,
          fontId: resolveFontId(settings.fontId),
        });
      },
    }),
    [settings, themes],
  );

  return (
    <ThemeSettingsContext.Provider value={value}>
      <MantineProvider
        theme={theme}
        cssVariablesResolver={cssVariablesResolver}
        defaultColorScheme="auto"
      >
        <WindowThemeSync
          themes={themes}
          settings={settings}
          onThemeApplied={onThemeApplied}
        />
        {children}
      </MantineProvider>
    </ThemeSettingsContext.Provider>
  );
}
