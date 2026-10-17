import { describe, expect, test } from "vitest";
import { DEFAULT_FONT_ID } from "../theme/fonts.ts";
import { generateTokens } from "../theme/generate.ts";
import { loadThemes } from "../theme/loadThemes.ts";
import {
  buildBackgroundCache,
  composeThemeTokens,
  resolveThemeSettings,
  sanitizePrimarySeed,
  serializeBackgroundCache,
} from "./themeSettings.ts";

const HEX = /^#[0-9a-fA-F]{6}$/;
const themes = loadThemes();
const beam = themes.find((theme) => theme.id === "beam");
if (!beam) {
  throw new Error("beam theme is missing");
}

describe("resolveThemeSettings", () => {
  test("broken JSON falls back to beam defaults", () => {
    expect(resolveThemeSettings("{not-json", themes)).toEqual({
      themeId: "beam",
      fontId: DEFAULT_FONT_ID,
    });
    expect(resolveThemeSettings(null, themes)).toEqual({
      themeId: "beam",
      fontId: DEFAULT_FONT_ID,
    });
    expect(resolveThemeSettings("", themes)).toEqual({
      themeId: "beam",
      fontId: DEFAULT_FONT_ID,
    });
  });

  test("unknown themeId falls back to beam", () => {
    expect(
      resolveThemeSettings(
        JSON.stringify({ themeId: "missing-theme" }),
        themes,
      ),
    ).toEqual({ themeId: "beam", fontId: DEFAULT_FONT_ID });
  });

  test("invalid seed is dropped", () => {
    expect(
      resolveThemeSettings(
        JSON.stringify({
          themeId: "ember",
          primarySeed: "not-a-color",
        }),
        themes,
      ),
    ).toEqual({ themeId: "ember", fontId: DEFAULT_FONT_ID });
  });

  test("valid themeId and seed are kept", () => {
    expect(
      resolveThemeSettings(
        JSON.stringify({
          themeId: "ember",
          primarySeed: "#EA580C",
        }),
        themes,
      ),
    ).toEqual({
      themeId: "ember",
      primarySeed: "#EA580C",
      fontId: DEFAULT_FONT_ID,
    });
  });
});

describe("sanitizePrimarySeed", () => {
  test("drops values culori cannot parse", () => {
    expect(sanitizePrimarySeed("not-a-color")).toBeUndefined();
    expect(sanitizePrimarySeed(42)).toBeUndefined();
    expect(sanitizePrimarySeed("#2563EB")).toBe("#2563EB");
  });
});

describe("buildBackgroundCache", () => {
  test("bg cache writer only outputs 6 digit hex", () => {
    const cache = buildBackgroundCache(beam.seeds);
    expect(cache.light).toMatch(HEX);
    expect(cache.dark).toMatch(HEX);
    expect(cache.light).toBe(generateTokens(beam.seeds, "light").background);
    expect(cache.dark).toBe(generateTokens(beam.seeds, "dark").background);

    const serialized = serializeBackgroundCache(cache);
    const parsed = JSON.parse(serialized) as {
      light: string;
      dark: string;
    };
    expect(parsed.light).toMatch(HEX);
    expect(parsed.dark).toMatch(HEX);
    expect(Object.keys(parsed).sort()).toEqual(["dark", "light"]);
  });
});

describe("composeThemeTokens", () => {
  test("matches generateTokens for beam light", () => {
    const composed = composeThemeTokens(themes, { themeId: "beam" }, "light");
    const direct = generateTokens(beam.seeds, "light");
    expect(composed.background).toBe(direct.background);
    expect(composed["text-primary"]).toBe(direct["text-primary"]);
    expect(composed.primary).toBe(direct.primary);
  });
});
