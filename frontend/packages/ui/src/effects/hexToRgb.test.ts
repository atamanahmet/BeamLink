import { describe, expect, test } from "vitest";
import { hexToRgb } from "./hexToRgb.ts";

describe("hexToRgb", () => {
  test("lowercase", () => {
    expect(hexToRgb("#ff8c00")).toEqual({ r: 255, g: 140, b: 0 });
  });

  test("uppercase", () => {
    expect(hexToRgb("#FFC832")).toEqual({ r: 255, g: 200, b: 50 });
  });

  test("black", () => {
    expect(hexToRgb("#000000")).toEqual({ r: 0, g: 0, b: 0 });
  });

  test("white", () => {
    expect(hexToRgb("#ffffff")).toEqual({ r: 255, g: 255, b: 255 });
  });

  test("short hex throws", () => {
    expect(() => hexToRgb("#fff")).toThrow();
  });

  test("missing hash throws", () => {
    expect(() => hexToRgb("ff8c00")).toThrow();
  });
});
