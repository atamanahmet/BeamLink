/**
 * Builds a filesystem path from breadcrumb segments.
 */
export function buildSegmentPath(
  currentPath: string,
  pathSegments: string[],
  index: number,
): string {
  const segs = pathSegments.slice(0, index + 1);
  if (currentPath.startsWith("/")) return "/" + segs.join("/");
  return segs.join("\\") + (segs.length === 1 ? "\\" : "");
}

/**
 * Splits a path into breadcrumb segments.
 */
export function pathToSegments(currentPath: string): string[] {
  const normalised = currentPath.replace(/\\/g, "/");
  return normalised ? normalised.split("/").filter(Boolean) : [];
}

/**
 * Footer confirm label for the current mode and selection.
 */
export function browserConfirmLabel(
  mode: "multi" | "folder",
  selectedCount: number,
  currentPath: string,
): string {
  if (mode === "folder") {
    if (selectedCount > 0) return "Select Folder";
    if (currentPath) return "Use This Folder";
    return "Select";
  }
  return `Send${selectedCount > 0 ? ` (${selectedCount})` : ""}`;
}

/**
 * Footer hint text for the current mode.
 */
export function browserHint(mode: "multi" | "folder"): string {
  if (mode === "folder") {
    return "Navigate to folder · Double-click to open · Click to select";
  }
  return "Click to select · Double-click folder to open";
}
