/**
 * Maps native registration state to the three phone status words.
 * Connected needs a confirmed TLS pin.
 */
export function connectionStatusWords(
  state: string | undefined,
  hasTokens: boolean,
  pinSet: boolean,
): string {
  if (!pinSet) {
    return "Not connected";
  }
  if (hasTokens || state === "APPROVED") {
    return "Connected";
  }
  if (state === "PENDING" || state === "registering") {
    return "Waiting for approval";
  }
  return "Not connected";
}

/**
 * True when the phone should open Connect first.
 */
export function phoneNeedsConnect(
  state: string | undefined,
  hasTokens: boolean,
  pinSet: boolean,
): boolean {
  return connectionStatusWords(state, hasTokens, pinSet) === "Not connected";
}
