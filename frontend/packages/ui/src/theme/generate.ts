import {
  converter,
  formatHex,
  toGamut,
  wcagContrast,
  type Oklch,
} from "culori";
import { STATUS_ROLES, STATUS_SEEDS } from "./statusSeeds.ts";
import type { ThemeMode, ThemeSeeds, ThemeTokens } from "./types.ts";

const toOklch = converter("oklch");
const toSrgb = toGamut("rgb", "oklch");

const LIGHTNESS = [
  0.97, 0.93, 0.87, 0.8, 0.72, 0.65, 0.52, 0.5, 0.42, 0.34,
] as const;

const CHROMA_FACTORS = [
  0.15, 0.3, 0.5, 0.7, 0.9, 1, 1, 0.95, 0.85, 0.75,
] as const;

const WHITE = "#ffffff";
const NEAR_BLACK = "#121212";

const SEED_ROLES = ["primary", "secondary", "tertiary"] as const;

const FILL_SHADE = { light: 6, dark: 4 } as const;
const SUBTLE_SHADE = { light: 1, dark: 8 } as const;
const TEXT_SHADE = { light: 8, dark: 1 } as const;

/**
 * Converts an OKLCH color to a clamped sRGB hex string.
 */
function oklchToHex(color: Oklch): string {
  const clamped = toSrgb(color);
  const hex = formatHex(clamped);
  if (!hex) {
    throw new Error("Failed to format color as hex");
  }
  return hex;
}

/**
 * Reads OKLCH channels from a seed hex color.
 */
function seedOklch(seedHex: string): Oklch {
  const color = toOklch(seedHex);
  if (!color) {
    throw new Error(`Invalid seed color: ${seedHex}`);
  }
  return {
    mode: "oklch",
    l: color.l,
    c: color.c ?? 0,
    h: color.h ?? 0,
  };
}

/**
 * Builds a 10 shade OKLCH scale from a seed hex color.
 * Shade 6 uses L 0.52 (was 0.58) so light primary clears 4.5 with white.
 */
export function generateScale(seedHex: string): string[] {
  const seed = seedOklch(seedHex);
  return LIGHTNESS.map((l, i) =>
    oklchToHex({
      mode: "oklch",
      l,
      c: seed.c * CHROMA_FACTORS[i],
      h: seed.h,
    }),
  );
}

/**
 * Returns the next shade after subtle for hover backgrounds.
 */
export function subtleHoverHex(seedHex: string, mode: ThemeMode): string {
  const scale = generateScale(seedHex);
  return scale[Math.min(SUBTLE_SHADE[mode] + 1, 9)];
}

/**
 * Builds a tinted gray scale with chroma capped at 0.02.
 */
export function generateNeutralScale(seedHex: string): string[] {
  const seed = seedOklch(seedHex);
  const chroma = Math.min(seed.c, 0.02);
  return LIGHTNESS.map((l, i) =>
    oklchToHex({
      mode: "oklch",
      l,
      c: chroma * CHROMA_FACTORS[i],
      h: seed.h,
    }),
  );
}

/**
 * Builds a low chroma surface or text color from a neutral hue.
 */
function neutralTone(hue: number, lightness: number, chroma = 0.01): string {
  return oklchToHex({
    mode: "oklch",
    l: lightness,
    c: chroma,
    h: hue,
  });
}

/**
 * Picks white or near black for the best contrast on a filled color.
 */
function pickForeground(fill: string): string {
  const whiteRatio = wcagContrast(fill, WHITE);
  const blackRatio = wcagContrast(fill, NEAR_BLACK);
  return whiteRatio >= blackRatio ? WHITE : NEAR_BLACK;
}

/**
 * Writes fill, foreground, subtle and text tokens for one color role.
 */
function assignRoleTokens(
  target: Record<string, string>,
  role: string,
  scale: string[],
  mode: ThemeMode,
): void {
  const fill = scale[FILL_SHADE[mode]];
  target[role] = fill;
  target[`${role}-foreground`] = pickForeground(fill);
  target[`${role}-subtle`] = scale[SUBTLE_SHADE[mode]];
  target[`${role}-text`] = scale[TEXT_SHADE[mode]];
}

/**
 * Builds semantic tokens for light or dark mode from theme seeds.
 */
export function generateTokens(
  seeds: ThemeSeeds,
  mode: ThemeMode,
): ThemeTokens {
  const neutral = seedOklch(seeds.neutral);
  const hue = neutral.h ?? 0;
  const tokens: Record<string, string> =
    mode === "light"
      ? {
          background: neutralTone(hue, 0.985),
          surface: neutralTone(hue, 0.965),
          glass: neutralTone(hue, 0.965),
          "surface-raised": neutralTone(hue, 0.99),
          "text-primary": neutralTone(hue, 0.22),
          "text-secondary": neutralTone(hue, 0.4),
          "text-muted": neutralTone(hue, 0.42),
          border: neutralTone(hue, 0.78),
          "border-strong": neutralTone(hue, 0.55),
          ring: neutralTone(hue, 0.52),
        }
      : {
          background: neutralTone(hue, 0.18),
          surface: neutralTone(hue, 0.22),
          glass: neutralTone(hue, 0.22),
          "surface-raised": neutralTone(hue, 0.26),
          "text-primary": neutralTone(hue, 0.95),
          "text-secondary": neutralTone(hue, 0.78),
          "text-muted": neutralTone(hue, 0.72),
          border: neutralTone(hue, 0.38),
          "border-strong": neutralTone(hue, 0.58),
          ring: neutralTone(hue, 0.72),
        };

  for (const role of SEED_ROLES) {
    assignRoleTokens(tokens, role, generateScale(seeds[role]), mode);
  }
  for (const role of STATUS_ROLES) {
    assignRoleTokens(tokens, role, generateScale(STATUS_SEEDS[role]), mode);
  }

  return tokens as ThemeTokens;
}
