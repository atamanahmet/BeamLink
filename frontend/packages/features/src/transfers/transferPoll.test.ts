import { describe, expect, it } from "vitest";
import type { TransferSummary } from "@beamlink/core";
import {
  getTransferPollInterval,
  hasActive,
  PENDING_STALL_MS,
} from "./transferPoll.ts";

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
    sourceDeviceId: null,
    targetDeviceId: "a",
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

const freshPendingAt = "2024-06-01T12:00:00.000Z";
const freshNow = new Date(freshPendingAt).getTime() + 1000;
const stalledNow =
  new Date(freshPendingAt).getTime() + PENDING_STALL_MS + 1000;

describe("hasActive", () => {
  it("empty list", () => {
    expect(hasActive([], freshNow)).toBe(false);
  });

  it("fresh PENDING", () => {
    expect(
      hasActive(
        [makeTransfer("PENDING", { createdAt: freshPendingAt })],
        freshNow,
      ),
    ).toBe(true);
  });

  it("stalled PENDING is not active", () => {
    expect(
      hasActive(
        [makeTransfer("PENDING", { createdAt: freshPendingAt })],
        stalledNow,
      ),
    ).toBe(false);
  });

  it("only SENDING", () => {
    expect(hasActive([makeTransfer("SENDING")], freshNow)).toBe(false);
  });

  it("only finished", () => {
    expect(
      hasActive(
        [
          makeTransfer("COMPLETED"),
          makeTransfer("FAILED"),
          makeTransfer("CANCELLED"),
        ],
        freshNow,
      ),
    ).toBe(false);
  });

  it("mixed", () => {
    expect(
      hasActive(
        [makeTransfer("COMPLETED"), makeTransfer("ACTIVE")],
        freshNow,
      ),
    ).toBe(true);
  });
});

describe("getTransferPollInterval", () => {
  it("empty list", () => {
    expect(getTransferPollInterval([], freshNow)).toBe(5000);
  });

  it("fresh PENDING", () => {
    expect(
      getTransferPollInterval(
        [makeTransfer("PENDING", { createdAt: freshPendingAt })],
        freshNow,
      ),
    ).toBe(500);
  });

  it("only stalled PENDING", () => {
    expect(
      getTransferPollInterval(
        [makeTransfer("PENDING", { createdAt: freshPendingAt })],
        stalledNow,
      ),
    ).toBe(2000);
  });

  it("only SENDING", () => {
    expect(getTransferPollInterval([makeTransfer("SENDING")], freshNow)).toBe(
      5000,
    );
  });

  it("only finished", () => {
    expect(
      getTransferPollInterval([makeTransfer("COMPLETED")], freshNow),
    ).toBe(5000);
  });

  it("mixed with fresh PENDING", () => {
    expect(
      getTransferPollInterval(
        [
          makeTransfer("COMPLETED"),
          makeTransfer("PENDING", { createdAt: freshPendingAt }),
        ],
        freshNow,
      ),
    ).toBe(500);
  });
});
