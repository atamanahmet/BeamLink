import path from "path";
import { describe, expect, test } from "vitest";
import { isAbsolutePathArg } from "./pathArg.ts";

describe("isAbsolutePathArg", () => {
  test("accepts absolute paths", () => {
    expect(isAbsolutePathArg(path.resolve("/tmp/demo"))).toBe(true);
    if (process.platform === "win32") {
      expect(isAbsolutePathArg("C:\\Users\\demo")).toBe(true);
    }
  });

  test("rejects relative, empty and non strings", () => {
    expect(isAbsolutePathArg("relative/path")).toBe(false);
    expect(isAbsolutePathArg("")).toBe(false);
    expect(isAbsolutePathArg(null)).toBe(false);
    expect(isAbsolutePathArg(12)).toBe(false);
  });
});
