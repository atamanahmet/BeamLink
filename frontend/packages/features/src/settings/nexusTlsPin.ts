export type NexusTlsPinStatus = {
  url: string;
  pinSet: boolean;
  suggestedUrl: string;
  pendingFingerprint: string;
};

export type NexusTlsPinAdapter = {
  getStatus: () => Promise<NexusTlsPinStatus>;
  probe: (ip: string, port: number) => Promise<{ url: string; fingerprint: string }>;
  confirm: (ip: string, port: number, fingerprint: string) => Promise<void>;
  cancel: () => Promise<void>;
  clearPin: () => Promise<void>;
};

/**
 * Fills the address field from stored URL or mDNS suggestion when empty.
 */
export function draftFromStatus(
  prev: string,
  url: string,
  suggestedUrl: string,
): string {
  return prev || url || suggestedUrl;
}

/**
 * True when mDNS offered an address and the person has not probed yet.
 */
export function shouldAutoProbe(
  pinSet: boolean,
  suggestedUrl: string,
  pendingFingerprint: string,
): boolean {
  return !pinSet && Boolean(suggestedUrl) && !pendingFingerprint;
}

/**
 * True once per launch when the device is UNREGISTERED and has no pin.
 */
export function shouldRedirectToPinSection(
  state: string | undefined,
  pinSet: boolean,
  alreadyRedirected: boolean,
): boolean {
  if (alreadyRedirected || pinSet) {
    return false;
  }
  return state === "UNREGISTERED";
}

/**
 * Splits https://host:port into ip and port.
 */
export function parseNexusTlsUrl(raw: string): { ip: string; port: number } {
  const trimmed = raw.trim();
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new Error("Invalid nexus address");
  }
  if (parsed.protocol !== "https:") {
    throw new Error("Only https:// addresses are allowed");
  }
  if (!parsed.hostname || parsed.port === "") {
    throw new Error("Address must include a host and port");
  }
  return { ip: parsed.hostname, port: Number(parsed.port) };
}
