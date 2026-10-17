import type { Platform } from "@beamlink/core";

type CreateFolderResult =
  | { ok: true }
  | { ok: false; error: string };

/**
 * Creates a folder under the current path.
 */
export async function createFolderUnder(
  platform: Platform,
  currentPath: string,
  name: string,
  sep: string,
): Promise<CreateFolderResult> {
  const fullPath = [currentPath, name].join(sep).replace(/[/\\]+/g, sep);

  if (await platform.exists(fullPath)) {
    return { ok: false, error: `"${name}" already exists.` };
  }

  if (/[/\\:\0]/.test(name)) {
    return { ok: false, error: "Name contains invalid characters." };
  }

  try {
    await platform.mkdir(fullPath);
    return { ok: true };
  } catch (e: unknown) {
    return {
      ok: false,
      error: (e as { message?: string }).message ?? "Could not create folder.",
    };
  }
}
