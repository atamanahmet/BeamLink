import { describe, expect, test } from "vitest";
import { dispatchNotice, shouldOpenTransfers } from "./dispatchNotice.ts";
import { dispatchErrorMessage } from "./dispatchErrorMessage.ts";

describe("dispatchNotice", () => {
  test("skipped count is the only notice", () => {
    expect(dispatchNotice(0, 3)).toBe(
      "Skipped 3 unreadable or system items.",
    );
    expect(dispatchNotice(2, 1)).toBe(
      "Skipped 1 unreadable or system items.",
    );
  });

  test("empty dispatch without skips", () => {
    expect(dispatchNotice(0, 0)).toBe(
      "Nothing was initiated. Check the selected paths.",
    );
    expect(dispatchNotice(1, 0)).toBeNull();
  });
});

describe("shouldOpenTransfers", () => {
  test("opens only when items started and nothing skipped", () => {
    expect(shouldOpenTransfers(1, 0)).toBe(true);
    expect(shouldOpenTransfers(1, 2)).toBe(false);
    expect(shouldOpenTransfers(0, 0)).toBe(false);
  });
});

describe("dispatchErrorMessage", () => {
  test("prefers response detail", () => {
    expect(
      dispatchErrorMessage({
        response: { data: { detail: "File operation failed: E:\\x" } },
        message: "Request failed",
      }),
    ).toBe("File operation failed: E:\\x");
  });

  test("falls back", () => {
    expect(dispatchErrorMessage(new Error("boom"))).toBe("boom");
    expect(dispatchErrorMessage({})).toBe("Dispatch failed.");
  });
});
