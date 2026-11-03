export const CONNECT_LABEL = "Connect";
export const LATER_LABEL = "Later";
export const BACK_LABEL = "Back";
export const CONFIRM_LABEL = "Confirm";
export const FIND_NEXUS_TITLE = "Find Nexus";
export const VERIFY_FINGERPRINT_TITLE = "Verify fingerprint";
export const WAITING_APPROVAL_TITLE = "Waiting for approval";
export const DEVICE_KEY_LABEL = "Device key";
export const NEXUS_CERTIFICATE_LABEL = "Nexus certificate";
export const FINGERPRINT_MATCH_TEXT =
  "Check that this matches the fingerprint shown in Nexus.";
export const WAITING_APPROVAL_BODY =
  "Open Nexus and approve this device. This window closes when it is approved.";
export const BANNER_DISCONNECTED = "This device is not connected to a Nexus.";
export const BANNER_CONNECT = "Connect to network";
export const BANNER_PENDING = "Waiting for approval in Nexus.";
export const BANNER_SHOW = "Show";
export const BANNER_CONNECT_AGAIN = "Connect again";
export const PHONE_START_LINE = "Connect to your Nexus to start.";
export const CONNECT_FAILED = "Connect failed";

export const DEVICE_STATE_UNREGISTERED = "UNREGISTERED";
export const DEVICE_STATE_PENDING_APPROVAL = "PENDING_APPROVAL";
export const DEVICE_STATE_APPROVED = "APPROVED";
export const DEVICE_STATE_REJECTED = "REJECTED";

export const PHONE_STATE_NOT_CONFIGURED = "not_configured";
export const PHONE_STATE_REGISTERING = "registering";
export const PHONE_STATE_PENDING = "PENDING";
export const PHONE_STATE_APPROVED = "APPROVED";
export const PHONE_STATE_REJECTED = "REJECTED";
export const PHONE_STATE_ERROR = "error";

export type ConnectBanner = {
  text: string;
  button: string;
};

/**
 * True when the device still needs the Connect modal.
 */
export function connectNeedsAction(state: string | undefined): boolean {
  return (
    state === DEVICE_STATE_UNREGISTERED ||
    state === DEVICE_STATE_PENDING_APPROVAL ||
    state === DEVICE_STATE_REJECTED
  );
}

/**
 * True when the Connect step is waiting for Nexus approval.
 */
export function connectWaiting(state: string | undefined): boolean {
  return (
    state === DEVICE_STATE_PENDING_APPROVAL ||
    state === PHONE_STATE_PENDING ||
    state === PHONE_STATE_REGISTERING
  );
}

/**
 * Banner copy for the device main page. Null when connected.
 */
export function connectBanner(state: string | undefined): ConnectBanner | null {
  if (state === DEVICE_STATE_UNREGISTERED) {
    return { text: BANNER_DISCONNECTED, button: BANNER_CONNECT };
  }
  if (state === DEVICE_STATE_PENDING_APPROVAL) {
    return { text: BANNER_PENDING, button: BANNER_SHOW };
  }
  if (state === DEVICE_STATE_REJECTED) {
    return { text: DEVICE_STATE_REJECTED, button: BANNER_CONNECT_AGAIN };
  }
  return null;
}
