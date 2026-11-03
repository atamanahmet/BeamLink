import { describe, expect, it } from "vitest";
import {
  destructiveDialogEscapeCloses,
  destructiveDialogPhase,
} from "./destructiveDialogPhase.ts";

describe("destructiveDialogPhase", () => {
  it("pending wins", () => {
    expect(destructiveDialogPhase(true, "done", "err")).toBe("pending");
  });

  it("success when result is set", () => {
    expect(destructiveDialogPhase(false, "done", null)).toBe("success");
  });

  it("failure when error is set", () => {
    expect(destructiveDialogPhase(false, null, "err")).toBe("failure");
  });

  it("idle when empty", () => {
    expect(destructiveDialogPhase(false, null, null)).toBe("idle");
  });
});

describe("destructiveDialogEscapeCloses", () => {
  it("blocks only pending", () => {
    expect(destructiveDialogEscapeCloses("pending")).toBe(false);
    expect(destructiveDialogEscapeCloses("idle")).toBe(true);
    expect(destructiveDialogEscapeCloses("success")).toBe(true);
    expect(destructiveDialogEscapeCloses("failure")).toBe(true);
  });
});
