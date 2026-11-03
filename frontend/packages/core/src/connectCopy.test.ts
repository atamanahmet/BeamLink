import { describe, expect, it } from "vitest";
import {
  DEVICE_STATE_APPROVED,
  DEVICE_STATE_PENDING_APPROVAL,
  DEVICE_STATE_REJECTED,
  DEVICE_STATE_UNREGISTERED,
  BANNER_CONNECT,
  BANNER_CONNECT_AGAIN,
  BANNER_DISCONNECTED,
  BANNER_PENDING,
  BANNER_SHOW,
  connectBanner,
  connectNeedsAction,
  connectWaiting,
  PHONE_STATE_PENDING,
  PHONE_STATE_REGISTERING,
} from "./connectCopy.ts";

describe("connectNeedsAction", () => {
  it("true for unregistered pending rejected", () => {
    expect(connectNeedsAction(DEVICE_STATE_UNREGISTERED)).toBe(true);
    expect(connectNeedsAction(DEVICE_STATE_PENDING_APPROVAL)).toBe(true);
    expect(connectNeedsAction(DEVICE_STATE_REJECTED)).toBe(true);
  });

  it("false for approved and empty", () => {
    expect(connectNeedsAction(DEVICE_STATE_APPROVED)).toBe(false);
    expect(connectNeedsAction(undefined)).toBe(false);
    expect(connectNeedsAction("")).toBe(false);
  });
});

describe("connectWaiting", () => {
  it("true for web and phone pending", () => {
    expect(connectWaiting(DEVICE_STATE_PENDING_APPROVAL)).toBe(true);
    expect(connectWaiting(PHONE_STATE_PENDING)).toBe(true);
    expect(connectWaiting(PHONE_STATE_REGISTERING)).toBe(true);
    expect(connectWaiting(DEVICE_STATE_UNREGISTERED)).toBe(false);
  });
});

describe("connectBanner", () => {
  it("unregistered", () => {
    expect(connectBanner(DEVICE_STATE_UNREGISTERED)).toEqual({
      text: BANNER_DISCONNECTED,
      button: BANNER_CONNECT,
    });
  });

  it("pending", () => {
    expect(connectBanner(DEVICE_STATE_PENDING_APPROVAL)).toEqual({
      text: BANNER_PENDING,
      button: BANNER_SHOW,
    });
  });

  it("rejected uses the real state word", () => {
    expect(connectBanner(DEVICE_STATE_REJECTED)).toEqual({
      text: DEVICE_STATE_REJECTED,
      button: BANNER_CONNECT_AGAIN,
    });
  });

  it("approved and unknown have no banner", () => {
    expect(connectBanner(DEVICE_STATE_APPROVED)).toBeNull();
    expect(connectBanner(undefined)).toBeNull();
  });
});
