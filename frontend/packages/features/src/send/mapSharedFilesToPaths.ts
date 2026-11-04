import type { SharedFileItem } from "@beamlink/core";

/**
 * Maps a native share payload to path strings for the upload list.
 */
export function mapSharedFilesToPaths(files: SharedFileItem[]): string[] {
  return files
    .map((file) => file.uri.trim())
    .filter((uri) => uri.length > 0);
}
