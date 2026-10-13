import fs from "fs";
import os from "os";
import path from "path";
import { afterEach, describe, expect, test } from "vitest";
import {
  isHexColor,
  readSavedWindowTheme,
  saveWindowTheme,
} from "./index.ts";

const fallback = { background: "#121212", foreground: "#f5f5f5" };

const tempDirs: string[] = [];

/**
 * Makes a unique temp directory for one test.
 */
function makeTempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "beamlink-window-theme-"));
  tempDirs.push(dir);
  return dir;
}

afterEach(() => {
  for (const dir of tempDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

describe("isHexColor", () => {
  test("accepts 6 digit hex colors", () => {
    expect(isHexColor("#abcdef")).toBe(true);
    expect(isHexColor("#ABCDEF")).toBe(true);
    expect(isHexColor("#121212")).toBe(true);
  });

  test("rejects invalid values", () => {
    expect(isHexColor("#abc")).toBe(false);
    expect(isHexColor("#abcdcfg")).toBe(false);
    expect(isHexColor("121212")).toBe(false);
    expect(isHexColor("#gg0000")).toBe(false);
    expect(isHexColor("")).toBe(false);
  });
});

describe("readSavedWindowTheme", () => {
  test("returns fallback when the file is missing", () => {
    const filePath = path.join(makeTempDir(), "missing.json");
    expect(readSavedWindowTheme(filePath, fallback)).toEqual(fallback);
  });

  test("returns fallback when JSON is broken", () => {
    const filePath = path.join(makeTempDir(), "broken.json");
    fs.writeFileSync(filePath, "{not-json", "utf-8");
    expect(readSavedWindowTheme(filePath, fallback)).toEqual(fallback);
  });

  test("returns fallback when colors are invalid", () => {
    const filePath = path.join(makeTempDir(), "bad.json");
    fs.writeFileSync(
      filePath,
      JSON.stringify({ background: "#fff", foreground: "red" }),
      "utf-8",
    );
    expect(readSavedWindowTheme(filePath, fallback)).toEqual(fallback);
  });
});

describe("saveWindowTheme and readSavedWindowTheme", () => {
  test("round trips valid colors in a temp directory", () => {
    const filePath = path.join(makeTempDir(), "window-theme.json");
    const colors = { background: "#1a0f0a", foreground: "#f5f5f5" };
    saveWindowTheme(filePath, colors);
    expect(readSavedWindowTheme(filePath, fallback)).toEqual(colors);
  });
});
