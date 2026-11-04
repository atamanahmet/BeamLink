/**
 * User-facing audit event labels for the nexus admin UI.
 */
export const AUDIT_EVENT_LABELS: Record<string, string> = {
  LOGIN: "Sign in",
  SETUP: "First-run setup",
  CREDENTIALS_CHANGED: "Credentials changed",
  PASSWORD_RESET: "Password reset",
  RECOVERY_CODE_REGENERATED: "Recovery code regenerated",
  LOGOUT_ALL: "Logout everywhere",
  ERASE_ALL: "Erase all",
  FACTORY_RESET: "Factory reset",
  DEVICE_APPROVED: "Device approved",
  DEVICE_REJECTED: "Device rejected",
  DEVICE_REMOVED: "Device removed",
  DEVICE_RENAMED: "Device renamed",
};
