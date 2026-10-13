import { converter, formatHex, parse } from "culori";
import { describe, expect, test } from "vitest";
import { checkTokens, CONTRAST_RULES } from "./contrast.ts";
import {
  generateNeutralScale,
  generateScale,
  generateTokens,
} from "./generate.ts";
import { loadThemes } from "./loadThemes.ts";
import { STATUS_ROLES } from "./statusSeeds.ts";
import type { ThemeMode, ThemeSeeds, ThemeTokens } from "./types.ts";

const toOklch = converter("oklch");
const HEX = /^#[0-9a-f]{6}$/i;
const modes: ThemeMode[] = ["light", "dark"];

describe("generateScale", () => {
  test("returns 10 valid hex strings with strictly decreasing OKLCH lightness", () => {
    const scale = generateScale("#2563EB");
    expect(scale).toHaveLength(10);
    const lightnesses = scale.map((hex) => {
      expect(hex).toMatch(HEX);
      const color = toOklch(hex);
      expect(color).toBeTruthy();
      return color!.l;
    });
    for (let i = 1; i < lightnesses.length; i += 1) {
      expect(lightnesses[i]).toBeLessThan(lightnesses[i - 1]);
    }
  });

  test("generated hex strings stay inside sRGB on round trip", () => {
    const scale = [
      ...generateScale("#2563EB"),
      ...generateNeutralScale("#64748B"),
    ];
    for (const hex of scale) {
      const parsed = parse(hex);
      expect(parsed).toBeTruthy();
      expect(formatHex(parsed!)).toBe(hex.toLowerCase());
    }
  });

  test("a seed with no chroma yields a gray scale", () => {
    const scale = generateScale("#808080");
    for (const hex of scale) {
      const color = toOklch(hex)!;
      expect(color.c ?? 0).toBeLessThan(0.001);
    }
  });
});

describe("generateTokens contrast", () => {
  const themes = loadThemes();
  expect(themes.length).toBeGreaterThan(0);

  test.each(
    themes.flatMap((theme) =>
      modes.map((mode) => ({ theme, mode })),
    ),
  )("$theme.id $mode passes every contrast rule", ({ theme, mode }) => {
    const tokens = generateTokens(theme.seeds, mode);
    const results = checkTokens(tokens);
    for (const result of results) {
      expect(result.pass, `${theme.id} ${mode} ${result.name}`).toBe(true);
    }
  });

  const weakSeeds = ["#FFFF99", "#000033", "#808080"] as const;
  const weakRoles = ["primary", "secondary", "tertiary"] as const;
  const beam = themes.find((theme) => theme.id === "beam");
  expect(beam).toBeTruthy();

  test.each(
    weakSeeds.flatMap((weak) =>
      weakRoles.flatMap((role) =>
        modes.map((mode) => ({ weak, role, mode })),
      ),
    ),
  )(
    "weak seed $weak as $role $mode passes every contrast rule",
    ({ weak, role, mode }) => {
      const seeds: ThemeSeeds = {
        primary: role === "primary" ? weak : beam!.seeds.primary,
        secondary: role === "secondary" ? weak : beam!.seeds.secondary,
        tertiary: role === "tertiary" ? weak : beam!.seeds.tertiary,
        neutral: beam!.seeds.neutral,
      };
      const tokens = generateTokens(seeds, mode);
      const results = checkTokens(tokens);
      for (const result of results) {
        expect(
          result.pass,
          `${weak} as ${role} ${mode} ${result.name} ratio=${result.ratio}`,
        ).toBe(true);
      }
    },
  );

  test("generateTokens is deterministic", () => {
    const seeds = beam!.seeds;
    expect(generateTokens(seeds, "light")).toEqual(
      generateTokens(seeds, "light"),
    );
    expect(generateTokens(seeds, "dark")).toEqual(
      generateTokens(seeds, "dark"),
    );
  });

  test("status tokens are identical for every theme", () => {
    const statusKeys = STATUS_ROLES.flatMap((role) => [
      role,
      `${role}-foreground`,
      `${role}-subtle`,
      `${role}-text`,
    ]) as (keyof ThemeTokens)[];
    for (const mode of modes) {
      const reference = generateTokens(themes[0].seeds, mode);
      for (const theme of themes) {
        const tokens = generateTokens(theme.seeds, mode);
        for (const key of statusKeys) {
          expect(tokens[key], `${theme.id} ${mode} ${key}`).toBe(
            reference[key],
          );
        }
      }
    }
  });
});

describe("checkTokens", () => {
  test("reports a failure for a deliberately bad pair", () => {
    const sample = loadThemes()[0];
    expect(sample).toBeTruthy();
    const bad = { ...generateTokens(sample.seeds, "light") };
    for (const key of Object.keys(bad) as (keyof ThemeTokens)[]) {
      bad[key] = "#ffffff";
    }
    bad.background = "#ffffff";
    bad.surface = "#f5f5f5";
    bad["text-primary"] = "#ffffff";
    const results = checkTokens(bad);
    expect(results).toHaveLength(CONTRAST_RULES.length);
    expect(results.some((result) => !result.pass)).toBe(true);
    const textOnBg = results.find(
      (result) => result.name === "text-primary on background",
    );
    expect(textOnBg?.pass).toBe(false);
  });
});
