import { parse } from "culori";
import { DEFAULT_FONT_ID, resolveFontId } from "../theme/fonts.ts";
import { generateTokens } from "../theme/generate.ts";
import type { ThemeFile, ThemeMode, ThemeSeeds, ThemeTokens } from "../theme/types.ts";

export const THEME_STORAGE_KEY = "beamlink-theme";
export const THEME_BG_STORAGE_KEY = "beamlink-theme-bg";
export const DEFAULT_THEME_ID = "beam";

export type ThemeSettings = {
  themeId: string;
  primarySeed?: string;
  fontId: string;
};

export type ThemeBackgroundCache = {
  light: string;
  dark: string;
};

/**
 * Returns a seed string only when culori can parse it.
 */
export function sanitizePrimarySeed(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  if (!parse(value)) {
    return undefined;
  }
  return value;
}

/**
 * Picks beam, or the first theme, when the id is missing.
 */
function fallbackThemeId(themes: ThemeFile[]): string {
  const beam = themes.find((theme) => theme.id === DEFAULT_THEME_ID);
  return beam?.id ?? themes[0]?.id ?? DEFAULT_THEME_ID;
}

/**
 * Resolves saved theme settings from a localStorage string.
 */
export function resolveThemeSettings(
  raw: string | null,
  themes: ThemeFile[],
): ThemeSettings {
  const fallbackId = fallbackThemeId(themes);
  if (raw == null || raw === "") {
    return { themeId: fallbackId, fontId: DEFAULT_FONT_ID };
  }

  try {
    const data: unknown = JSON.parse(raw);
    if (!data || typeof data !== "object") {
      return { themeId: fallbackId, fontId: DEFAULT_FONT_ID };
    }

    const record = data as Record<string, unknown>;
    const requestedId =
      typeof record.themeId === "string" ? record.themeId : fallbackId;
    const themeId = themes.some((theme) => theme.id === requestedId)
      ? requestedId
      : fallbackId;
    const primarySeed = sanitizePrimarySeed(record.primarySeed);
    const fontId = resolveFontId(record.fontId);

    return primarySeed === undefined
      ? { themeId, fontId }
      : { themeId, primarySeed, fontId };
  } catch {
    return { themeId: fallbackId, fontId: DEFAULT_FONT_ID };
  }
}

/**
 * Builds light and dark background hex values from seeds.
 */
export function buildBackgroundCache(seeds: ThemeSeeds): ThemeBackgroundCache {
  return {
    light: generateTokens(seeds, "light").background,
    dark: generateTokens(seeds, "dark").background,
  };
}

/**
 * Serializes a background cache that only contains 6 digit hex values.
 */
export function serializeBackgroundCache(
  cache: ThemeBackgroundCache,
): string {
  const hex = /^#[0-9a-fA-F]{6}$/;
  if (!hex.test(cache.light) || !hex.test(cache.dark)) {
    throw new Error("Background cache must use 6 digit hex colors");
  }
  return JSON.stringify({ light: cache.light, dark: cache.dark });
}

/**
 * Merges a theme file with an optional primary seed override.
 */
export function resolveThemeSeeds(
  themes: ThemeFile[],
  settings: Pick<ThemeSettings, "themeId" | "primarySeed">,
): ThemeSeeds {
  const theme =
    themes.find((item) => item.id === settings.themeId) ??
    themes.find((item) => item.id === DEFAULT_THEME_ID) ??
    themes[0];
  if (!theme) {
    throw new Error("No themes are available");
  }
  if (!settings.primarySeed) {
    return theme.seeds;
  }
  return { ...theme.seeds, primary: settings.primarySeed };
}

/**
 * Single path from theme settings to resolved color tokens.
 */
export function composeThemeTokens(
  themes: ThemeFile[],
  settings: Pick<ThemeSettings, "themeId" | "primarySeed">,
  colorScheme: ThemeMode,
): ThemeTokens {
  const tokens = generateTokens(
    resolveThemeSeeds(themes, settings),
    colorScheme,
  );
  return { ...tokens, glass: tokens.surface };
}
