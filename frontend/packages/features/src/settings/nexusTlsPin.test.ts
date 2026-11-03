import { describe, expect, it } from "vitest";
import {
  draftFromStatus,
  parseNexusTlsUrl,
  shouldAutoProbe,
  shouldRedirectToPinSection,
} from "./nexusTlsPin.ts";

describe("parseNexusTlsUrl", () => {
  it("splits https host and port", () => {
    expect(parseNexusTlsUrl("https://192.168.1.10:7473")).toEqual({
      ip: "192.168.1.10",
      port: 7473,
    });
  });

  it("refuses http", () => {
    expect(() => parseNexusTlsUrl("http://192.168.1.10:7473")).toThrow(
      "Only https:// addresses are allowed",
    );
  });
});

describe("draftFromStatus", () => {
  it("keeps typed text", () => {
    expect(draftFromStatus("https://1.1.1.1:1", "https://2.2.2.2:2", "https://3.3.3.3:3")).toBe(
      "https://1.1.1.1:1",
    );
  });

  it("uses suggestion when empty", () => {
    expect(draftFromStatus("", "", "https://192.168.1.10:7473")).toBe(
      "https://192.168.1.10:7473",
    );
  });
});

describe("shouldAutoProbe", () => {
  it("probes when suggestion exists and no pin", () => {
    expect(shouldAutoProbe(false, "https://192.168.1.10:7473", "")).toBe(true);
  });

  it("skips when pin set or fingerprint already shown", () => {
    expect(shouldAutoProbe(true, "https://192.168.1.10:7473", "")).toBe(false);
    expect(shouldAutoProbe(false, "https://192.168.1.10:7473", "abcd")).toBe(false);
    expect(shouldAutoProbe(false, "", "")).toBe(false);
  });
});

describe("shouldRedirectToPinSection", () => {
  it("redirects unregistered with no pin once", () => {
    expect(shouldRedirectToPinSection("UNREGISTERED", false, false)).toBe(true);
  });

  it("does not loop after one redirect", () => {
    expect(shouldRedirectToPinSection("UNREGISTERED", false, true)).toBe(false);
  });

  it("skips when pin set or not unregistered", () => {
    expect(shouldRedirectToPinSection("UNREGISTERED", true, false)).toBe(false);
    expect(shouldRedirectToPinSection("APPROVED", false, false)).toBe(false);
    expect(shouldRedirectToPinSection(undefined, false, false)).toBe(false);
  });
});
