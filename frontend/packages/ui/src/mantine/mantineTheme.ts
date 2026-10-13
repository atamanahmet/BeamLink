import type {
  ConvertCSSVariablesInput,
  MantineColorsTuple,
  MantineThemeOverride,
  VariantColorsResolver,
} from "@mantine/core";
import { defaultVariantColorsResolver, rem } from "@mantine/core";
import {
  generateScale,
  generateTokens,
  subtleHoverHex,
} from "../theme/generate.ts";
import { STATUS_ROLES, STATUS_SEEDS } from "../theme/statusSeeds.ts";
import type { ThemeMode, ThemeSeeds, ThemeTokens } from "../theme/types.ts";

const BEAM_ROLES = [
  "primary",
  "secondary",
  "tertiary",
  "success",
  "warning",
  "danger",
  "info",
] as const;

type BeamRole = (typeof BEAM_ROLES)[number];

const SEED_ROLE_SEEDS = ["primary", "secondary", "tertiary"] as const;

/**
 * Maps a Mantine color name to a Beam role token prefix.
 */
function roleFromColor(
  color: string | undefined,
  primaryColor: string,
): BeamRole | null {
  const name = color ?? primaryColor;
  if (name === primaryColor || name === "brand") {
    return "primary";
  }
  for (const role of BEAM_ROLES) {
    if (role !== "primary" && role === name) {
      return role;
    }
  }
  return null;
}

/**
 * Uses Mantine defaults, then applies Beam role colors per variant.
 */
function beamVariantResolver(): VariantColorsResolver {
  return (input) => {
    const result = defaultVariantColorsResolver(input);
    const role = roleFromColor(input.color, input.theme.primaryColor);
    if (!role) {
      return result;
    }
    const text = `var(--bl-${role}-text)`;
    const foreground = `var(--bl-${role}-foreground)`;
    const subtle = `var(--bl-${role}-subtle)`;
    const subtleHover = `var(--bl-${role}-subtle-hover)`;
    if (input.variant === "filled") {
      return { ...result, color: foreground };
    }
    if (input.variant === "light") {
      return {
        ...result,
        background: subtle,
        hover: subtleHover,
        color: text,
        border: `${rem(1)} solid transparent`,
      };
    }
    if (input.variant === "outline") {
      return {
        ...result,
        background: "transparent",
        hover: subtleHover,
        color: text,
        border: `${rem(1)} solid ${text}`,
      };
    }
    if (input.variant === "subtle") {
      return {
        ...result,
        background: "transparent",
        hover: subtleHover,
        color: text,
        border: `${rem(1)} solid transparent`,
      };
    }
    return result;
  };
}

const MANTINE_VARIABLES = {
  background: "--mantine-color-body",
  surface: "--mantine-color-default",
  "text-primary": "--mantine-color-text",
  "text-secondary": "--mantine-color-dimmed",
  "border-strong": "--mantine-color-default-border",
  "primary-foreground": "--mantine-primary-color-contrast",
} as const satisfies Partial<Record<keyof ThemeTokens, `--${string}`>>;

const PLACEHOLDER_VARIABLE = "--mantine-color-placeholder";
const INPUT_BORDER_VARIABLE = "--input-bd";
const INPUT_BORDER_TOKEN = "var(--bl-border-strong)";
const INPUT_BG_VARIABLE = "--input-bg";
const INPUT_BG_TOKEN = "var(--bl-background)";
const SURFACE = "var(--bl-surface)";
const SURFACE_RAISED = "var(--bl-surface-raised)";

type SchemeVariables = ConvertCSSVariablesInput["light"];

/**
 * Maps every token to a --bl-* CSS variable.
 */
function beamVariables(tokens: ThemeTokens): Record<string, string> {
  const result: Record<string, string> = {};
  for (const token of Object.keys(tokens) as (keyof ThemeTokens)[]) {
    result[`--bl-${token}`] = tokens[token];
  }
  return result;
}

/**
 * Adds per-role subtle hover shades for the active mode.
 */
function subtleHoverVariables(
  seeds: ThemeSeeds,
  mode: ThemeMode,
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const role of SEED_ROLE_SEEDS) {
    result[`--bl-${role}-subtle-hover`] = subtleHoverHex(seeds[role], mode);
  }
  for (const role of STATUS_ROLES) {
    result[`--bl-${role}-subtle-hover`] = subtleHoverHex(
      STATUS_SEEDS[role],
      mode,
    );
  }
  return result;
}

/**
 * Copies a generated scale into the 10 color tuple Mantine expects.
 */
function toColorTuple(scale: string[]): MantineColorsTuple {
  if (scale.length < 10) {
    throw new Error("Color scale must contain 10 shades");
  }
  return [
    scale[0],
    scale[1],
    scale[2],
    scale[3],
    scale[4],
    scale[5],
    scale[6],
    scale[7],
    scale[8],
    scale[9],
  ];
}

/**
 * Throws when any seed hex cannot be turned into a color scale.
 */
function rejectInvalidSeeds(seeds: ThemeSeeds): void {
  generateScale(seeds.primary);
  generateScale(seeds.secondary);
  generateScale(seeds.tertiary);
  generateScale(seeds.neutral);
}

/**
 * Maps token values onto CSS variable names.
 */
function tokenVariables(
  tokens: ThemeTokens,
  names: Partial<Record<keyof ThemeTokens, `--${string}`>>,
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const token of Object.keys(names) as (keyof ThemeTokens)[]) {
    const cssName = names[token];
    if (!cssName) {
      continue;
    }
    result[cssName] = tokens[token];
  }
  return result;
}

/**
 * Builds the light or dark CSS variable set from generated tokens.
 */
function schemeVariables(seeds: ThemeSeeds, mode: ThemeMode): SchemeVariables {
  const tokens = generateTokens(seeds, mode);
  return {
    ...tokenVariables(tokens, MANTINE_VARIABLES),
    ...beamVariables(tokens),
    ...subtleHoverVariables(seeds, mode),
    [PLACEHOLDER_VARIABLE]: tokens["text-secondary"],
  };
}

/**
 * Sets Input border and fill; leaves error and success alone.
 */
function inputBorderVars(
  _theme: unknown,
  props: { error?: unknown; success?: unknown },
): { wrapper: Record<string, string | undefined> } {
  if (props.error || props.success) {
    return { wrapper: {} };
  }
  return {
    wrapper: {
      [INPUT_BORDER_VARIABLE]: INPUT_BORDER_TOKEN,
      [INPUT_BG_VARIABLE]: INPUT_BG_TOKEN,
    },
  };
}

/**
 * Builds a Mantine theme override from theme seeds.
 */
export function buildMantineTheme(seeds: ThemeSeeds): MantineThemeOverride {
  rejectInvalidSeeds(seeds);
  const statusColors: Record<string, MantineColorsTuple> = {};
  for (const role of STATUS_ROLES) {
    statusColors[role] = toColorTuple(generateScale(STATUS_SEEDS[role]));
  }
  return {
    colors: {
      brand: toColorTuple(generateScale(seeds.primary)),
      secondary: toColorTuple(generateScale(seeds.secondary)),
      tertiary: toColorTuple(generateScale(seeds.tertiary)),
      ...statusColors,
    },
    primaryColor: "brand",
    primaryShade: { light: 6, dark: 4 },
    autoContrast: false,
    fontFamily: "var(--font-ui)",
    fontFamilyMonospace: "var(--font-mono)",
    variantColorResolver: beamVariantResolver(),
    components: {
      Input: {
        vars: inputBorderVars,
      },
      Paper: {
        styles: {
          root: {
            backgroundColor: SURFACE,
          },
        },
      },
      Modal: {
        styles: {
          content: {
            backgroundColor: SURFACE_RAISED,
          },
        },
      },
      Popover: {
        styles: {
          dropdown: {
            backgroundColor: SURFACE_RAISED,
          },
        },
      },
      Menu: {
        styles: {
          dropdown: {
            backgroundColor: SURFACE_RAISED,
          },
        },
      },
      Combobox: {
        styles: {
          dropdown: {
            backgroundColor: SURFACE_RAISED,
          },
        },
      },
    },
  };
}

/**
 * Builds the cssVariablesResolver result for light and dark schemes.
 */
export function buildCssVariables(seeds: ThemeSeeds): ConvertCSSVariablesInput {
  rejectInvalidSeeds(seeds);
  return {
    variables: {},
    light: schemeVariables(seeds, "light"),
    dark: schemeVariables(seeds, "dark"),
  };
}
