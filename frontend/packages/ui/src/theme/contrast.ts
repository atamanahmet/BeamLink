import { wcagContrast } from "culori";
import { STATUS_ROLES } from "./statusSeeds.ts";
import type { ThemeTokens } from "./types.ts";

export type ContrastRule = {
  name: string;
  foreground: keyof ThemeTokens;
  background: keyof ThemeTokens;
  minimum: number;
  below?: {
    foreground: keyof ThemeTokens;
    background: keyof ThemeTokens;
  };
};

export type ContrastCheckResult = {
  name: string;
  ratio: number;
  minimum: number;
  pass: boolean;
};

const SEED_ROLES = ["primary", "secondary", "tertiary"] as const;
const BASE_SURFACES = ["background", "surface", "surface-raised"] as const;
const COLOR_ROLES = [...SEED_ROLES, ...STATUS_ROLES] as const;

/**
 * Builds the full contrast rule list, including role and status loops.
 */
function buildContrastRules(): ContrastRule[] {
  const rules: ContrastRule[] = [
    {
      name: "text-primary on background",
      foreground: "text-primary",
      background: "background",
      minimum: 4.5,
    },
    {
      name: "text-primary on surface",
      foreground: "text-primary",
      background: "surface",
      minimum: 4.5,
    },
    {
      name: "text-secondary on background",
      foreground: "text-secondary",
      background: "background",
      minimum: 4.5,
    },
    {
      name: "text-secondary on surface",
      foreground: "text-secondary",
      background: "surface",
      minimum: 4.5,
    },
    {
      name: "primary-foreground on primary",
      foreground: "primary-foreground",
      background: "primary",
      minimum: 4.5,
    },
    {
      name: "secondary-foreground on secondary",
      foreground: "secondary-foreground",
      background: "secondary",
      minimum: 4.5,
    },
    {
      name: "tertiary-foreground on tertiary",
      foreground: "tertiary-foreground",
      background: "tertiary",
      minimum: 4.5,
    },
    {
      name: "border-strong on background",
      foreground: "border-strong",
      background: "background",
      minimum: 3,
    },
    {
      name: "text-primary on surface-raised",
      foreground: "text-primary",
      background: "surface-raised",
      minimum: 4.5,
    },
    {
      name: "text-secondary on surface-raised",
      foreground: "text-secondary",
      background: "surface-raised",
      minimum: 4.5,
    },
    {
      name: "border on surface",
      foreground: "border",
      background: "surface",
      minimum: 1.3,
      below: { foreground: "border-strong", background: "surface" },
    },
  ];

  for (const surface of BASE_SURFACES) {
    rules.push({
      name: `text-muted on ${surface}`,
      foreground: "text-muted",
      background: surface,
      minimum: 4.5,
    });
    rules.push({
      name: `ring on ${surface}`,
      foreground: "ring",
      background: surface,
      minimum: 3,
    });
  }

  for (const role of COLOR_ROLES) {
    const textKey = `${role}-text` as keyof ThemeTokens;
    const subtleKey = `${role}-subtle` as keyof ThemeTokens;
    for (const surface of BASE_SURFACES) {
      rules.push({
        name: `${role}-text on ${surface}`,
        foreground: textKey,
        background: surface,
        minimum: 4.5,
      });
    }
    rules.push({
      name: `${role}-text on ${role}-subtle`,
      foreground: textKey,
      background: subtleKey,
      minimum: 4.5,
    });
    rules.push({
      name: `text-primary on ${role}-subtle`,
      foreground: "text-primary",
      background: subtleKey,
      minimum: 4.5,
    });
  }

  for (const role of STATUS_ROLES) {
    rules.push({
      name: `${role}-foreground on ${role}`,
      foreground: `${role}-foreground` as keyof ThemeTokens,
      background: role,
      minimum: 4.5,
    });
  }

  return rules;
}

export const CONTRAST_RULES: ContrastRule[] = buildContrastRules();

/**
 * Returns the WCAG contrast ratio between two colors via culori.
 */
export function contrastRatio(a: string, b: string): number {
  return wcagContrast(a, b);
}

/**
 * Checks every contrast rule against a token set without throwing.
 */
export function checkTokens(tokens: ThemeTokens): ContrastCheckResult[] {
  return CONTRAST_RULES.map((rule) => {
    const ratio = contrastRatio(
      tokens[rule.foreground],
      tokens[rule.background],
    );
    let pass = ratio >= rule.minimum;
    if (pass && rule.below) {
      const ceiling = contrastRatio(
        tokens[rule.below.foreground],
        tokens[rule.below.background],
      );
      pass = ratio < ceiling;
    }
    return {
      name: rule.name,
      ratio,
      minimum: rule.minimum,
      pass,
    };
  });
}
