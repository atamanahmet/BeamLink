import { describe, expect, it } from "vitest";
import { bytesToGib, gibToBytes } from "./bytes.ts";

describe("gibToBytes", () => {
  it("maps 1 GiB to 1073741824", () => {
    expect(gibToBytes(1)).toBe(1073741824);
  });

  it("maps 0.25 GiB to 268435456", () => {
    expect(gibToBytes(0.25)).toBe(268435456);
  });
});

describe("bytesToGib", () => {
  it("maps 1073741824 to 1", () => {
    expect(bytesToGib(1073741824)).toBe(1);
  });
});
