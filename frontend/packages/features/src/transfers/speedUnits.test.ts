import { describe, expect, test } from "vitest";
import {
  DEFAULT_SPEED_UNIT,
  formatMbps,
  fromMbps,
  parseSpeedInput,
  roundSpeedDisplay,
  toMbps,
} from "./speedUnits.ts";

describe("speedUnits", () => {
  test("default unit is Mb/s", () => {
    expect(DEFAULT_SPEED_UNIT).toBe("Mb/s");
  });

  test("toMbps from KB/s uses 0.008", () => {
    expect(toMbps(1, "KB/s")).toBeCloseTo(0.008);
  });

  test("toMbps from MB/s uses 8", () => {
    expect(toMbps(1, "MB/s")).toBe(8);
  });

  test("toMbps from Mb/s is identity", () => {
    expect(toMbps(12.5, "Mb/s")).toBe(12.5);
  });

  test("toMbps from Gb/s uses 1000", () => {
    expect(toMbps(1, "Gb/s")).toBe(1000);
  });

  test("fromMbps to KB/s reverses 0.008", () => {
    expect(fromMbps(0.008, "KB/s")).toBeCloseTo(1);
  });

  test("fromMbps to MB/s reverses 8", () => {
    expect(fromMbps(8, "MB/s")).toBe(1);
  });

  test("fromMbps to Gb/s reverses 1000", () => {
    expect(fromMbps(1000, "Gb/s")).toBe(1);
  });

  test("formatMbps includes unit label", () => {
    expect(formatMbps(8, "MB/s")).toBe("1.00 MB/s");
  });

  test("roundSpeedDisplay uses no decimals at 100+", () => {
    expect(roundSpeedDisplay(125.4)).toBe("125");
  });

  test("roundSpeedDisplay uses one decimal at 10+", () => {
    expect(roundSpeedDisplay(12.56)).toBe("12.6");
  });

  test("parseSpeedInput empty returns null", () => {
    expect(parseSpeedInput("", "Mb/s")).toBeNull();
  });

  test("parseSpeedInput converts MB/s to Mb/s", () => {
    expect(parseSpeedInput("2", "MB/s")).toBe(16);
  });

  test("parseSpeedInput rejects non positive", () => {
    expect(Number.isNaN(parseSpeedInput("0", "Mb/s"))).toBe(true);
  });
});
