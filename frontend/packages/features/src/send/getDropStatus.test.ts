import { describe, expect, it } from "vitest";
import { getDropStatus } from "./getDropStatus.ts";

const base = {
  isSending: false,
  hasPeer: true,
  isOffline: false,
  isDragging: false,
};

describe("getDropStatus", () => {
  it("sending alone", () => {
    expect(getDropStatus({ ...base, isSending: true })).toBe("sending");
  });

  it("noPeer alone", () => {
    expect(getDropStatus({ ...base, hasPeer: false })).toBe("noPeer");
  });

  it("offline alone", () => {
    expect(getDropStatus({ ...base, isOffline: true })).toBe("offline");
  });

  it("dragging alone", () => {
    expect(getDropStatus({ ...base, isDragging: true })).toBe("dragging");
  });

  it("idle when all false", () => {
    expect(getDropStatus(base)).toBe("idle");
  });

  it("sending over noPeer", () => {
    expect(
      getDropStatus({ ...base, isSending: true, hasPeer: false }),
    ).toBe("sending");
  });

  it("sending over offline", () => {
    expect(
      getDropStatus({ ...base, isSending: true, isOffline: true }),
    ).toBe("sending");
  });

  it("sending over dragging", () => {
    expect(
      getDropStatus({ ...base, isSending: true, isDragging: true }),
    ).toBe("sending");
  });

  it("noPeer over offline", () => {
    expect(
      getDropStatus({ ...base, hasPeer: false, isOffline: true }),
    ).toBe("noPeer");
  });

  it("noPeer over dragging", () => {
    expect(
      getDropStatus({ ...base, hasPeer: false, isDragging: true }),
    ).toBe("noPeer");
  });

  it("offline over dragging", () => {
    expect(
      getDropStatus({ ...base, isOffline: true, isDragging: true }),
    ).toBe("offline");
  });
});
