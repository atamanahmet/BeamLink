/**
 * User-facing recovery and erase texts for the nexus admin UI.
 */
export const recoveryCopy = {
  forgotLink: "Forgot password?",
  lostCodeLink: "I lost my recovery code",
  resetTitle: "Reset password",
  resetWarning:
    "All other sessions will be logged out. A NEW recovery code is shown once. Save it.",
  eraseTitle: "Erase all",
  eraseBody:
    "All nexus data is erased. Every device and the phone are removed from this network and must register again and be approved. History is deleted. This cannot be undone.",
  eraseWord: "ERASE",
  setupHint: "Save this code. It is shown once.",
  savedCheckbox: "I saved this code",
  continue: "Continue",
  copy: "Copy",
  copied: "Copied",
  recoveryLabel: "Recovery code",
  noCodeYet: "No code yet",
  regenerate: "Regenerate",
  regenerateTitle: "Regenerate recovery code",
  regenerateBody: "The old recovery code stops working.",
  currentPassword: "Current password",
} as const;
