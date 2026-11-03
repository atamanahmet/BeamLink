import { describe, expect, it } from "vitest";
import { parseRetryAfter } from "./parseRetryAfter.ts";

describe("parseRetryAfter", () => {
  it("valid", () => {
    expect(parseRetryAfter("1")).toBe(1);
    expect(parseRetryAfter("60")).toBe(60);
  });

  it("null", () => {
    expect(parseRetryAfter(null)).toBeNull();
  });

  it("empty", () => {
    expect(parseRetryAfter("")).toBeNull();
  });

  it("0", () => {
    expect(parseRetryAfter("0")).toBeNull();
  });

  it("-5", () => {
    expect(parseRetryAfter("-5")).toBeNull();
  });

  it("abc", () => {
    expect(parseRetryAfter("abc")).toBeNull();
  });

  it("12.5", () => {
    expect(parseRetryAfter("12.5")).toBeNull();
  });
});
