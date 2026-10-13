import { describe, expect, test } from "vitest";
import {
  DEFAULT_FONT_ID,
  FONTS,
  resolveFont,
  resolveFontId,
} from "./fonts.ts";

describe("resolveFontId", () => {
  test("unknown id falls back to the default", () => {
    expect(resolveFontId("missing-font")).toBe(DEFAULT_FONT_ID);
    expect(resolveFontId(null)).toBe(DEFAULT_FONT_ID);
    expect(resolveFontId(42)).toBe(DEFAULT_FONT_ID);
  });

  test("known id resolves", () => {
    for (const font of FONTS) {
      expect(resolveFontId(font.id)).toBe(font.id);
      expect(resolveFont(font.id)).toEqual(font);
    }
  });
});
