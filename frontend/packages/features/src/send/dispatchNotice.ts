/**
 * One notice after dispatch. Skipped entries win over the empty-path text.
 */
export function dispatchNotice(
  itemCount: number,
  skippedCount: number,
): string | null {
  if (skippedCount > 0) {
    return `Skipped ${skippedCount} unreadable or system items.`;
  }
  if (itemCount === 0) {
    return "Nothing was initiated. Check the selected paths.";
  }
  return null;
}

/**
 * True when dispatch started transfers and nothing was skipped.
 */
export function shouldOpenTransfers(
  itemCount: number,
  skippedCount: number,
): boolean {
  return itemCount > 0 && skippedCount === 0;
}
