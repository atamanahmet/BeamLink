export {
  checkTokens,
  CONTRAST_RULES,
  contrastRatio,
  type ContrastCheckResult,
  type ContrastRule,
} from "./theme/contrast.ts";
export {
  generateNeutralScale,
  generateScale,
  generateTokens,
} from "./theme/generate.ts";
export {
  DEFAULT_FONT_ID,
  FONTS,
  fontFamilyStack,
  resolveFont,
  resolveFontId,
  type FontEntry,
} from "./theme/fonts.ts";
export { loadThemes } from "./theme/loadThemes.ts";
export type {
  ThemeFile,
  ThemeMode,
  ThemeSeeds,
  ThemeTokens,
} from "./theme/types.ts";

export { ThemeController } from "./mantine/ThemeController.tsx";
export { buildCssVariables, buildMantineTheme } from "./mantine/mantineTheme.ts";
export {
  buildBackgroundCache,
  composeThemeTokens,
  DEFAULT_THEME_ID,
  resolveThemeSeeds,
  resolveThemeSettings,
  sanitizePrimarySeed,
  serializeBackgroundCache,
  THEME_BG_STORAGE_KEY,
  THEME_STORAGE_KEY,
  type ThemeBackgroundCache,
  type ThemeSettings,
} from "./mantine/themeSettings.ts";
export { useThemeTokens } from "./mantine/useThemeTokens.ts";
export {
  ThemeSettingsContext,
  useThemeSettings,
  type ThemeSettingsContextValue,
} from "./mantine/themeSettingsContext.tsx";
export {
  AppFrame,
  type AppFrameNavItem,
  type AppFrameProps,
} from "./shell/AppFrame.tsx";
export { AuthCard, type AuthCardProps } from "./shell/AuthCard.tsx";
export { RouteErrorScreen } from "./shell/RouteErrorScreen.tsx";
export { GLASS_SURFACE_ALPHA, glassSurfaceStyle } from "./shell/glass.ts";
export { blendGlassOverWarp } from "./shell/blendGlassOverWarp.ts";

export { WarpBackground } from "./effects/WarpBackground.tsx";
