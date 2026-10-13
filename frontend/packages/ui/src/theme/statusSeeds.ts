/**
 * Fixed status seeds shared by every theme.
 */
export const STATUS_SEEDS = {
  success: "#16A34A",
  warning: "#F59E0B",
  danger: "#DC2626",
  info: "#0284C7",
} as const;

export type StatusRole = keyof typeof STATUS_SEEDS;

export const STATUS_ROLES = Object.keys(STATUS_SEEDS) as StatusRole[];
