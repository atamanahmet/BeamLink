import { describe, expect, test } from "vitest";
import { applyProgress, getPhoneTransfers } from "./phoneTransferStore.ts";

describe("applyProgress canResume", () => {
  test("paused send and receive both allow resume", () => {
    applyProgress({
      bytes: 10,
      total: 100,
      state: "paused",
      error: "paused",
      direction: "send",
    });
    applyProgress({
      bytes: 4,
      total: 20,
      state: "paused",
      error: "paused",
      direction: "receive",
    });
    const send = getPhoneTransfers().find((t) => t.id === "send");
    const receive = getPhoneTransfers().find((t) => t.id === "receive");
    expect(send?.canResume).toBe(true);
    expect(receive?.canResume).toBe(true);
  });

  test("active send has no resume", () => {
    applyProgress({
      bytes: 10,
      total: 100,
      state: "sending",
      direction: "send",
    });
    const send = getPhoneTransfers().find((t) => t.id === "send");
    expect(send?.canResume).toBe(false);
  });
});
