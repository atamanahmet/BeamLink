import { describe, expect, test } from "vitest";
import type { TransferSummary } from "@beamlink/core";
import { countByState, filterTransfers } from "./transferFilter.ts";

function row(status: string): TransferSummary {
  return {
    id: status,
    dispatchId: null,
    type: "SINGLE",
    name: status,
    status,
    totalSize: 1,
    confirmedBytes: 0,
    totalFiles: 1,
    sourceAgentId: null,
    targetAgentId: "t",
    targetIp: "1.1.1.1",
    targetPort: 1,
    createdAt: "2020-01-01T00:00:00Z",
    completedAt: null,
    activeTransferMs: 0,
    failureReason: null,
    pausedBy: null,
    canResume: false,
    speedLimitMbps: null,
    effectiveLimitMbps: null,
  };
}

describe("transferFilter", () => {
  test("filterTransfers ALL returns all rows", () => {
    const rows = [row("ACTIVE"), row("FAILED")];
    expect(filterTransfers(rows, "ALL")).toHaveLength(2);
  });

  test("filterTransfers ACTIVE keeps only ACTIVE", () => {
    const rows = [row("ACTIVE"), row("PAUSED")];
    expect(filterTransfers(rows, "ACTIVE").map((t) => t.status)).toEqual([
      "ACTIVE",
    ]);
  });

  test("filterTransfers PAUSED keeps only PAUSED", () => {
    const rows = [row("PAUSED"), row("PENDING")];
    expect(filterTransfers(rows, "PAUSED")).toHaveLength(1);
  });

  test("filterTransfers PENDING keeps only PENDING", () => {
    expect(filterTransfers([row("PENDING"), row("ACTIVE")], "PENDING")).toHaveLength(
      1,
    );
  });

  test("filterTransfers COMPLETED keeps only COMPLETED", () => {
    expect(
      filterTransfers([row("COMPLETED"), row("FAILED")], "COMPLETED"),
    ).toHaveLength(1);
  });

  test("filterTransfers FAILED keeps only FAILED", () => {
    expect(
      filterTransfers([row("FAILED"), row("CANCELLED")], "FAILED"),
    ).toHaveLength(1);
  });

  test("countByState ALL equals list length", () => {
    const rows = [row("ACTIVE"), row("CANCELLED")];
    expect(countByState(rows).ALL).toBe(2);
  });

  test("countByState counts PAUSED", () => {
    const rows = [row("PAUSED"), row("PAUSED"), row("ACTIVE")];
    expect(countByState(rows).PAUSED).toBe(2);
  });

  test("countByState ignores CANCELLED in chips", () => {
    const counts = countByState([row("CANCELLED")]);
    expect(counts.FAILED).toBe(0);
    expect(counts.COMPLETED).toBe(0);
    expect(counts.ALL).toBe(1);
  });
});
