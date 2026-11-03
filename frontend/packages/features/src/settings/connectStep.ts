import { connectWaiting } from "@beamlink/core";

export type ConnectStep = "find" | "verify" | "waiting";

/**
 * Picks the Connect step from pin, fingerprint, and device state.
 */
export function connectStep(
  deviceState: string | undefined,
  pinSet: boolean,
  pendingFingerprint: string,
): ConnectStep {
  if (connectWaiting(deviceState)) {
    return "waiting";
  }
  if (pendingFingerprint && !pinSet) {
    return "verify";
  }
  return "find";
}
