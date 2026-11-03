import { describe, expect, it } from "vitest";
import {
  DEVICE_STATE_PENDING_APPROVAL,
  DEVICE_STATE_UNREGISTERED,
  PHONE_STATE_PENDING,
} from "@beamlink/core";
import { connectStep } from "./connectStep.ts";

describe("connectStep", () => {
  it("waiting when pending approval", () => {
    expect(connectStep(DEVICE_STATE_PENDING_APPROVAL, false, "")).toBe(
      "waiting",
    );
    expect(connectStep(PHONE_STATE_PENDING, false, "")).toBe("waiting");
  });

  it("verify when fingerprint is shown", () => {
    expect(connectStep(DEVICE_STATE_UNREGISTERED, false, "abcd")).toBe(
      "verify",
    );
  });

  it("find when no pin and no fingerprint", () => {
    expect(connectStep(DEVICE_STATE_UNREGISTERED, false, "")).toBe("find");
  });
});
