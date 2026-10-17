import { describe, expect, test } from "vitest";
import { resolveTransferPeerId } from "./resolveTransferPeerId.ts";

describe("resolveTransferPeerId", () => {
  test("sender row uses targetAgentId", () => {
    expect(resolveTransferPeerId("agent-a", "agent-a", "nexus-1")).toBe(
      "nexus-1",
    );
  });

  test("receiver row uses sourceAgentId", () => {
    expect(resolveTransferPeerId("agent-a", "nexus-1", null)).toBe("nexus-1");
    expect(resolveTransferPeerId("agent-a", "nexus-1", "agent-a")).toBe(
      "nexus-1",
    );
  });

  test("missing id returns null", () => {
    expect(resolveTransferPeerId("agent-a", null, null)).toBeNull();
    expect(resolveTransferPeerId(null, null, null)).toBeNull();
  });
});
