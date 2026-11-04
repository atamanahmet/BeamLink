export type AppSenderGuardOptions = {
  packaged: boolean;
  appIndexUrl: string;
  devOrigin: string;
  caseInsensitivePath: boolean;
};

export type ExactAppSenderInput = AppSenderGuardOptions & {
  senderUrl: string;
};

/**
 * Normalizes a file URL path for exact comparison.
 */
function normalizeFilePathname(
  pathname: string,
  caseInsensitivePath: boolean,
): string | null {
  let decoded: string;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return null;
  }
  const unified = decoded.replace(/\\/g, "/");
  const parts = unified.split("/");
  for (const part of parts) {
    if (part === "..") {
      return null;
    }
  }
  const collapsed = parts
    .filter((part) => part !== "" && part !== ".")
    .join("/");
  const normalized = `/${collapsed}`;
  return caseInsensitivePath ? normalized.toLowerCase() : normalized;
}

/**
 * Returns true when the sender URL is exactly the app page.
 */
export function isExactAppSenderUrl(input: ExactAppSenderInput): boolean {
  let sender: URL;
  let expected: URL;
  try {
    sender = new URL(input.senderUrl);
    expected = new URL(
      input.packaged ? input.appIndexUrl : input.devOrigin,
    );
  } catch {
    return false;
  }

  if (input.packaged) {
    if (sender.protocol !== "file:" || expected.protocol !== "file:") {
      return false;
    }
    const senderPath = normalizeFilePathname(
      sender.pathname,
      input.caseInsensitivePath,
    );
    const expectedPath = normalizeFilePathname(
      expected.pathname,
      input.caseInsensitivePath,
    );
    if (senderPath == null || expectedPath == null) {
      return false;
    }
    return senderPath === expectedPath;
  }

  if (sender.protocol === "file:") {
    return false;
  }
  if (sender.origin !== expected.origin) {
    return false;
  }
  const path = sender.pathname === "" ? "/" : sender.pathname;
  return path === "/";
}

type SenderCheckEvent = {
  senderFrame?: { url: string } | null;
  sender?: { mainFrame?: unknown } | null;
};

/**
 * Rejects when the sender is not the app top frame.
 */
export function rejectNonAppSender(
  event: SenderCheckEvent,
  options: AppSenderGuardOptions,
): { ok: false; code: "INVALID_SENDER" } | null {
  const frame = event.senderFrame;
  if (!frame || frame !== event.sender?.mainFrame) {
    return { ok: false, code: "INVALID_SENDER" };
  }
  if (
    !isExactAppSenderUrl({
      senderUrl: frame.url,
      packaged: options.packaged,
      appIndexUrl: options.appIndexUrl,
      devOrigin: options.devOrigin,
      caseInsensitivePath: options.caseInsensitivePath,
    })
  ) {
    return { ok: false, code: "INVALID_SENDER" };
  }
  return null;
}
