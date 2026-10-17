import { describe, expect, it } from "vitest";
import type { TransferSummary } from "@beamlink/core";
import { getTransferPollInterval, hasActive } from "./transferPoll.ts";

function makeTransfer(
  status: string,
  overrides: Partial<TransferSummary> = {},
): TransferSummary {
  return {
    id: "t1",
    dispatchId: null,
    type: "SINGLE",
    name: "file",
    status,
    totalSize: 0,
    confirmedBytes: 0,
    totalFiles: 1,
    sourceAgentId: null,
    targetAgentId: "a",
    targetIp: "1.1.1.1",
    targetPort: 1,
    createdAt: "2020-01-01T00:00:00.000Z",
    completedAt: null,
    activeTransferMs: 0,
    failureReason: null,
    pausedBy: null,
    canResume: false,
    speedLimitMbps: null,
    effectiveLimitMbps: null,
    ...overrides,
  };
}

describe("hasActive", () => {
  it("empty list", () => {
    expect(hasActive([])).toBe(false);
  });

  it("only PENDING", () => {
    expect(hasActive([makeTransfer("PENDING")])).toBe(true);
  });

  it("only SENDING", () => {
    expect(hasActive([makeTransfer("SENDING")])).toBe(false);
  });

  it("only finished", () => {
    expect(
      hasActive([
        makeTransfer("COMPLETED"),
        makeTransfer("FAILED"),
        makeTransfer("CANCELLED"),
      ]),
    ).toBe(false);
  });

  it("mixed", () => {
    expect(
      hasActive([makeTransfer("COMPLETED"), makeTransfer("ACTIVE")]),
    ).toBe(true);
  });
});

describe("getTransferPollInterval", () => {
  it("empty list", () => {
    expect(getTransferPollInterval([])).toBe(5000);
  });

  it("only PENDING", () => {
    expect(getTransferPollInterval([makeTransfer("PENDING")])).toBe(500);
  });

  it("only SENDING", () => {
    expect(getTransferPollInterval([makeTransfer("SENDING")])).toBe(5000);
  });

  it("only finished", () => {
    expect(getTransferPollInterval([makeTransfer("COMPLETED")])).toBe(5000);
  });

  it("mixed", () => {
    expect(
      getTransferPollInterval([
        makeTransfer("COMPLETED"),
        makeTransfer("PENDING"),
      ]),
    ).toBe(500);
  });
});
