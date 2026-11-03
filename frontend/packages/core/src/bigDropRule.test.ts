import { describe, expect, it } from "vitest";
import { BIG_DROP_FILE_LIMIT, bigDropRule } from "./bigDropRule.ts";

describe("bigDropRule", () => {
  it("one file no", () => {
    expect(bigDropRule({ hasFolder: false, fileCount: 1 })).toBe(false);
  });

  it("3 files no", () => {
    expect(bigDropRule({ hasFolder: false, fileCount: 3 })).toBe(false);
  });

  it("1000 files no", () => {
    expect(
      bigDropRule({ hasFolder: false, fileCount: BIG_DROP_FILE_LIMIT }),
    ).toBe(false);
  });

  it("1001 files yes", () => {
    expect(
      bigDropRule({ hasFolder: false, fileCount: BIG_DROP_FILE_LIMIT + 1 }),
    ).toBe(true);
  });

  it("one folder with 1 file yes", () => {
    expect(bigDropRule({ hasFolder: true, fileCount: 1 })).toBe(true);
  });

  it("folder with 5000 files yes", () => {
    expect(bigDropRule({ hasFolder: true, fileCount: 5000 })).toBe(true);
  });

  it("empty drop no", () => {
    expect(bigDropRule({ hasFolder: false, fileCount: 0 })).toBe(false);
  });
});
