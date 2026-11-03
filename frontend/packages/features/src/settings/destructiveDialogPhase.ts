export type DestructiveDialogPhase = "idle" | "pending" | "success" | "failure";

/**
 * Picks idle, pending, success, or failure for a destructive confirm dialog.
 */
export function destructiveDialogPhase(
  pending: boolean,
  result: string | null | undefined,
  error: string | null | undefined,
): DestructiveDialogPhase {
  if (pending) {
    return "pending";
  }
  if (result) {
    return "success";
  }
  if (error) {
    return "failure";
  }
  return "idle";
}

/**
 * True when Escape may close. Pending blocks Escape.
 */
export function destructiveDialogEscapeCloses(
  phase: DestructiveDialogPhase,
): boolean {
  return phase !== "pending";
}
