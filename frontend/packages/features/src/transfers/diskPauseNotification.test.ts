import { describe, expect, test } from "vitest";
import type { TransferSummary } from "@beamlink/core";
import {
  diskPauseNotificationCopy,
  isReceiveDiskPause,
} from "./diskPauseNotification.ts";

function row(
  status: string,
  failureReason: string | null,
  targetDeviceId: string,
): TransferSummary {
  return {
    id: "t1",
    dispatchId: null,
    type: "SINGLE",
    name: "a.bin",
    status,
    totalSize: 1,
    confirmedBytes: 0,
    totalFiles: 1,
    sourceDeviceId: "src",
    targetDeviceId,
    targetIp: "127.0.0.1",
    targetPort: 9090,
    createdAt: new Date().toISOString(),
    completedAt: null,
    activeTransferMs: 0,
    failureReason,
    pausedBy: "TARGET",
    canResume: true,
    speedLimitMbps: null,
    effectiveLimitMbps: null,
  };
}

describe("isReceiveDiskPause", () => {
  test("true only for receive PAUSED INSUFFICIENT_DISK", () => {
    expect(
      isReceiveDiskPause(row("PAUSED", "INSUFFICIENT_DISK", "me"), "me"),
    ).toBe(true);
    expect(
      isReceiveDiskPause(row("PAUSED", "INSUFFICIENT_DISK", "other"), "me"),
    ).toBe(false);
    expect(isReceiveDiskPause(row("PAUSED", "INSUFFICIENT_DISK", "me"), null)).toBe(
      false,
    );
    expect(isReceiveDiskPause(row("ACTIVE", "INSUFFICIENT_DISK", "me"), "me")).toBe(
      false,
    );
    expect(isReceiveDiskPause(row("PAUSED", "OTHER", "me"), "me")).toBe(false);
  });
});

describe("diskPauseNotificationCopy", () => {
  test("splits the mapper text", () => {
    expect(diskPauseNotificationCopy()).toEqual({
      title: "Not enough disk space.",
      body: "Free some space, then resume.",
    });
  });
});
