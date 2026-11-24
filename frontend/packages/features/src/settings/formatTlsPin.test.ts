import { describe, expect, it } from "vitest";
import { formatTlsPin } from "./formatTlsPin.ts";

describe("formatTlsPin", () => {
  it("groups a 64-hex pin into four lines", () => {
    const pin = "a".repeat(64);
    expect(formatTlsPin(pin)).toBe(
      ["a".repeat(16), "a".repeat(16), "a".repeat(16), "a".repeat(16)].join(
        "\n",
      ),
    );
  });

  it("groups a 32-hex device key into two lines", () => {
    const key = "b".repeat(32);
    expect(formatTlsPin(key)).toBe(
      ["b".repeat(16), "b".repeat(16)].join("\n"),
    );
  });

  it("leaves unknown values unchanged", () => {
    expect(formatTlsPin("not-hex")).toBe("not-hex");
  });
});
