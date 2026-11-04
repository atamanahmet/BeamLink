import { describe, expect, test } from "vitest";
import { resolveTransferPeerId } from "./resolveTransferPeerId.ts";

describe("resolveTransferPeerId", () => {
  test("sender row uses targetDeviceId", () => {
    expect(resolveTransferPeerId("device-a", "device-a", "nexus-1")).toBe(
      "nexus-1",
    );
  });

  test("receiver row uses sourceDeviceId", () => {
    expect(resolveTransferPeerId("device-a", "nexus-1", null)).toBe("nexus-1");
    expect(resolveTransferPeerId("device-a", "nexus-1", "device-a")).toBe(
      "nexus-1",
    );
  });

  test("missing id returns null", () => {
    expect(resolveTransferPeerId("device-a", null, null)).toBeNull();
    expect(resolveTransferPeerId(null, null, null)).toBeNull();
  });
});
