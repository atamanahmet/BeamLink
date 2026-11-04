import { describe, expect, it } from "vitest";
import { isStalledPending, PENDING_STALL_MS } from "./transferPoll.ts";

describe("isStalledPending", () => {
  const createdAt = "2020-01-01T00:00:00.000Z";
  const createdMs = new Date(createdAt).getTime();

  it("under 30s is not stalled", () => {
    expect(
      isStalledPending("PENDING", createdAt, createdMs + PENDING_STALL_MS - 1),
    ).toBe(false);
  });

  it("at 30s is stalled", () => {
    expect(
      isStalledPending("PENDING", createdAt, createdMs + PENDING_STALL_MS),
    ).toBe(true);
  });

  it("over 30s is stalled", () => {
    expect(
      isStalledPending("PENDING", createdAt, createdMs + PENDING_STALL_MS + 1),
    ).toBe(true);
  });

  it("not PENDING is never stalled", () => {
    expect(
      isStalledPending("ACTIVE", createdAt, createdMs + PENDING_STALL_MS + 1),
    ).toBe(false);
    expect(
      isStalledPending("COMPLETED", createdAt, createdMs + PENDING_STALL_MS),
    ).toBe(false);
  });
});
