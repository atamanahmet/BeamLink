import { describe, expect, test } from "vitest";
import type { TransferSummary } from "@beamlink/core";
import { groupTransfers } from "./transferGrouping.ts";

function row(
  partial: Partial<TransferSummary> & Pick<TransferSummary, "id" | "type" | "status">,
): TransferSummary {
  return {
    dispatchId: "d1",
    name: "n",
    totalSize: 1,
    confirmedBytes: 0,
    totalFiles: 1,
    sourceAgentId: null,
    targetAgentId: "t1",
    targetIp: "127.0.0.1",
    targetPort: 1,
    createdAt: "2026-01-01T00:00:00Z",
    completedAt: null,
    activeTransferMs: 0,
    failureReason: null,
    pausedBy: null,
    canResume: false,
    speedLimitMbps: null,
    effectiveLimitMbps: null,
    ...partial,
  };
}

describe("groupTransfers status", () => {
  test("uses BATCH DTO status for dispatch card", () => {
    const { groups } = groupTransfers([
      row({ id: "1", type: "BATCH", status: "PARTIAL", dispatchId: "d1" }),
      row({ id: "2", type: "SINGLE", status: "FAILED", dispatchId: "d1" }),
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0].status).toBe("PARTIAL");
  });

  test("falls back to first member status when no group row", () => {
    const { groups } = groupTransfers([
      row({ id: "1", type: "SINGLE", status: "ACTIVE", dispatchId: "d1" }),
      row({ id: "2", type: "SINGLE", status: "PENDING", dispatchId: "d1" }),
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0].status).toBe("ACTIVE");
  });
});
