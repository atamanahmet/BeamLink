import { describe, expect, test } from "vitest";
import {
  statusBadgeColor,
  statusWord,
  toTransferStatus,
} from "./phoneTransferMap.ts";

describe("toTransferStatus", () => {
  test("maps native progress states", () => {
    expect(toTransferStatus("sending")).toBe("ACTIVE");
    expect(toTransferStatus("receiving")).toBe("ACTIVE");
    expect(toTransferStatus("paused")).toBe("PAUSED");
    expect(toTransferStatus("completed")).toBe("COMPLETED");
    expect(toTransferStatus("cancelled")).toBe("CANCELLED");
    expect(toTransferStatus("error")).toBe("FAILED");
  });
});

describe("statusBadgeColor", () => {
  test("maps status to badge color", () => {
    expect(statusBadgeColor("COMPLETED")).toBe("success");
    expect(statusBadgeColor("FAILED")).toBe("danger");
    expect(statusBadgeColor("ACTIVE")).toBe("brand");
    expect(statusBadgeColor("PAUSED")).toBe("gray");
  });
});

describe("statusWord", () => {
  test("uses send and receive words", () => {
    expect(statusWord("ACTIVE", "send")).toBe("Sending");
    expect(statusWord("ACTIVE", "receive")).toBe("Receiving");
    expect(statusWord("COMPLETED", "send")).toBe("Done");
    expect(statusWord("FAILED", "receive")).toBe("Failed");
  });
});
