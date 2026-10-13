import {
  DEFAULT_THEME,
  defaultVariantColorsResolver,
  mergeMantineTheme,
} from "@mantine/core";
import { describe, expect, test } from "vitest";
import { generateScale, generateTokens } from "../theme/generate.ts";
import { loadThemes } from "../theme/loadThemes.ts";
import type { ThemeMode, ThemeSeeds, ThemeTokens } from "../theme/types.ts";
import { buildCssVariables, buildMantineTheme } from "./mantineTheme.ts";

const HEX = /^#[0-9a-fA-F]{6}$/;
const modes: ThemeMode[] = ["light", "dark"];

const MANTINE_VARIABLES = {
  "--mantine-color-body": "background",
  "--mantine-color-default": "surface",
  "--mantine-color-text": "text-primary",
  "--mantine-color-dimmed": "text-secondary",
  "--mantine-color-default-border": "border-strong",
  "--mantine-primary-color-contrast": "primary-foreground",
} as const satisfies Record<`--${string}`, keyof ThemeTokens>;

const shipped = loadThemes();
const beam = shipped.find((theme) => theme.id === "beam");
if (!beam) {
  throw new Error("beam theme is missing");
}

const paleSeeds: ThemeSeeds = {
  primary: "#FFFF99",
  secondary: beam.seeds.secondary,
  tertiary: beam.seeds.tertiary,
  neutral: beam.seeds.neutral,
};

describe("buildMantineTheme", () => {
  test("brand scale has 10 entries and primary shade matches generateTokens", () => {
    for (const themeFile of shipped) {
      const theme = buildMantineTheme(themeFile.seeds);
      const brand = theme.colors?.brand;
      expect(brand).toHaveLength(10);
      expect(brand).toEqual(generateScale(themeFile.seeds.primary));
      expect(theme.colors?.secondary).toEqual(
        generateScale(themeFile.seeds.secondary),
      );
      expect(theme.colors?.tertiary).toEqual(
        generateScale(themeFile.seeds.tertiary),
      );
      expect(theme.primaryColor).toBe("brand");
      expect(theme.primaryShade).toEqual({ light: 6, dark: 4 });
      expect(theme.autoContrast).toBe(false);
      expect(theme.colors?.gray).toBeUndefined();
      expect(theme.colors?.dark).toBeUndefined();
      expect(brand?.[6]).toBe(generateTokens(themeFile.seeds, "light").primary);
      expect(brand?.[4]).toBe(generateTokens(themeFile.seeds, "dark").primary);
    }
  });

  test("an invalid seed throws", () => {
    const seeds: ThemeSeeds = { ...beam.seeds, primary: "not-a-color" };
    expect(() => buildMantineTheme(seeds)).toThrowError(
      "Invalid seed color: not-a-color",
    );
  });

  test("Input vars map default border to bl-border-strong", () => {
    const theme = buildMantineTheme(beam.seeds);
    const vars = theme.components?.Input?.vars;
    expect(typeof vars).toBe("function");
    const normal = vars!(DEFAULT_THEME, {}, undefined);
    expect(normal.wrapper["--input-bd"]).toBe("var(--bl-border-strong)");
    expect(normal.wrapper["--input-bg"]).toBe("var(--bl-background)");
    const errored = vars!(DEFAULT_THEME, { error: "bad" }, undefined);
    expect(errored.wrapper["--input-bd"]).toBeUndefined();
    expect(errored.wrapper["--input-bg"]).toBeUndefined();
  });

  test("Paper styles use bl-surface and raised overlays use surface-raised", () => {
    const theme = buildMantineTheme(beam.seeds);
    expect(theme.components?.Paper?.styles).toEqual({
      root: { backgroundColor: "var(--bl-surface)" },
    });
    expect(theme.components?.Modal?.styles).toEqual({
      content: { backgroundColor: "var(--bl-surface-raised)" },
    });
    for (const name of ["Popover", "Menu", "Combobox"] as const) {
      expect(theme.components?.[name]?.styles).toEqual({
        dropdown: { backgroundColor: "var(--bl-surface-raised)" },
      });
    }
  });

  const beamRoles = [
    { color: "brand", role: "primary" },
    { color: "secondary", role: "secondary" },
    { color: "tertiary", role: "tertiary" },
    { color: "success", role: "success" },
    { color: "warning", role: "warning" },
    { color: "danger", role: "danger" },
    { color: "info", role: "info" },
  ] as const;

  test("filled Beam roles keep fill background and use role-foreground", () => {
    const full = mergeMantineTheme(DEFAULT_THEME, buildMantineTheme(beam.seeds));
    for (const { color, role } of beamRoles) {
      const ours = full.variantColorResolver({
        color,
        theme: full,
        variant: "filled",
      });
      const theirs = defaultVariantColorsResolver({
        color,
        theme: full,
        variant: "filled",
      });
      expect(ours.background).toBe(theirs.background);
      expect(ours.hover).toBe(theirs.hover);
      expect(ours.color).toBe(`var(--bl-${role}-foreground)`);
    }
  });

  test("light Beam roles use subtle background and role-text", () => {
    const full = mergeMantineTheme(DEFAULT_THEME, buildMantineTheme(beam.seeds));
    for (const { color, role } of beamRoles) {
      const ours = full.variantColorResolver({
        color,
        theme: full,
        variant: "light",
      });
      expect(ours.background).toBe(`var(--bl-${role}-subtle)`);
      expect(ours.color).toBe(`var(--bl-${role}-text)`);
      expect(ours.hover).toBe(`var(--bl-${role}-subtle-hover)`);
    }
  });

  test("outline Beam roles use role-text for color and border", () => {
    const full = mergeMantineTheme(DEFAULT_THEME, buildMantineTheme(beam.seeds));
    for (const { color, role } of beamRoles) {
      const ours = full.variantColorResolver({
        color,
        theme: full,
        variant: "outline",
      });
      expect(ours.color).toBe(`var(--bl-${role}-text)`);
      expect(ours.border).toContain(`var(--bl-${role}-text)`);
      expect(ours.hover).toBe(`var(--bl-${role}-subtle-hover)`);
    }
  });

  test("subtle Beam roles use role-text and subtle hover", () => {
    const full = mergeMantineTheme(DEFAULT_THEME, buildMantineTheme(beam.seeds));
    for (const { color, role } of beamRoles) {
      const ours = full.variantColorResolver({
        color,
        theme: full,
        variant: "subtle",
      });
      expect(ours.color).toBe(`var(--bl-${role}-text)`);
      expect(ours.background).toBe("transparent");
      expect(ours.hover).toBe(`var(--bl-${role}-subtle-hover)`);
    }
  });

  test("filled red remains the Mantine default color", () => {
    const full = mergeMantineTheme(DEFAULT_THEME, buildMantineTheme(beam.seeds));
    const filledRed = full.variantColorResolver({
      color: "red",
      theme: full,
      variant: "filled",
    });
    expect(filledRed.color).toBe(
      defaultVariantColorsResolver({
        color: "red",
        theme: full,
        variant: "filled",
      }).color,
    );
  });

  test("status colors are registered as 10 shade tuples", () => {
    const theme = buildMantineTheme(beam.seeds);
    for (const role of ["success", "warning", "danger", "info"] as const) {
      expect(theme.colors?.[role]).toHaveLength(10);
    }
  });
});

describe("buildCssVariables", () => {
  test("light and dark sets contain every --bl variable from generateTokens", () => {
    const css = buildCssVariables(beam.seeds);
    for (const mode of modes) {
      const tokens = generateTokens(beam.seeds, mode);
      for (const token of Object.keys(tokens) as (keyof ThemeTokens)[]) {
        const cssName = `--bl-${token}` as `--bl-${string}`;
        expect(css[mode][cssName]).toBe(tokens[token]);
        expect(css[mode][cssName]).toMatch(HEX);
      }
      for (const [cssName, token] of Object.entries(MANTINE_VARIABLES) as [
        keyof typeof MANTINE_VARIABLES,
        keyof ThemeTokens,
      ][]) {
        expect(css[mode][cssName]).toBe(tokens[token]);
      }
    }
  });

  test("primary contrast text equals the generator foreground", () => {
    const css = buildCssVariables(paleSeeds);
    for (const mode of modes) {
      const foreground = generateTokens(paleSeeds, mode)["primary-foreground"];
      expect(css[mode]["--mantine-primary-color-contrast"]).toBe(foreground);
    }
    expect(generateTokens(paleSeeds, "dark")["primary-foreground"]).toBe(
      "#121212",
    );
    expect(css.dark["--mantine-primary-color-contrast"]).toBe("#121212");
  });

  test("an invalid seed throws", () => {
    const seeds: ThemeSeeds = { ...beam.seeds, neutral: "not-a-color" };
    expect(() => buildCssVariables(seeds)).toThrowError(
      "Invalid seed color: not-a-color",
    );
  });

  test("every CSS value is a 6 digit hex", () => {
    for (const themeFile of [...shipped, { seeds: paleSeeds }]) {
      const css = buildCssVariables(themeFile.seeds);
      const values = [
        ...Object.values(css.variables),
        ...Object.values(css.light),
        ...Object.values(css.dark),
      ];
      expect(values.length).toBeGreaterThan(0);
      for (const value of values) {
        expect(value).toMatch(HEX);
      }
    }
  });

  test("placeholder variable matches text-secondary token", () => {
    for (const mode of modes) {
      const tokens = generateTokens(beam.seeds, mode);
      const css = buildCssVariables(beam.seeds);
      expect(css[mode]["--mantine-color-placeholder"]).toBe(
        tokens["text-secondary"],
      );
    }
  });
});
