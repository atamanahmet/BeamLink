import { describe, expect, test } from "vitest";
import { DEFAULT_SPEED_UNIT } from "./speedUnits.ts";
import { resolveSpeedUnit } from "./unitPreference.ts";

describe("unitPreference", () => {
  test("resolveSpeedUnit returns default for null", () => {
    expect(resolveSpeedUnit(null)).toBe(DEFAULT_SPEED_UNIT);
  });

  test("resolveSpeedUnit accepts MB/s", () => {
    expect(resolveSpeedUnit("MB/s")).toBe("MB/s");
  });

  test("resolveSpeedUnit falls back for unknown", () => {
    expect(resolveSpeedUnit("nope")).toBe(DEFAULT_SPEED_UNIT);
  });
});
