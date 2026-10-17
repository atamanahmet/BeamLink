import type { FsEntry } from "./fileBrowserShared.ts";

/**
 * Toggles selection for multi or folder mode.
 */
export function nextSelection(
  prev: Set<string>,
  entry: FsEntry,
  mode: "multi" | "folder",
): Set<string> {
  if (mode === "folder") {
    return prev.has(entry.fullPath) ? new Set() : new Set([entry.fullPath]);
  }
  const next = new Set(prev);
  if (next.has(entry.fullPath)) next.delete(entry.fullPath);
  else next.add(entry.fullPath);
  return next;
}

/**
 * Removes one path from the selection set.
 */
export function withoutPath(prev: Set<string>, fullPath: string): Set<string> {
  const next = new Set(prev);
  next.delete(fullPath);
  return next;
}
