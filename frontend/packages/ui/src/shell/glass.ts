import type { CSSProperties } from "react";

/** Glass fill strength for frosted surfaces. */
export const GLASS_SURFACE_ALPHA = 0.85;

/**
 * Shared glass surface style for AppFrame and AuthCard.
 */
export function glassSurfaceStyle(): CSSProperties {
  return {
    backgroundColor: `color-mix(in srgb, var(--bl-glass) ${GLASS_SURFACE_ALPHA * 100}%, transparent)`,
    backdropFilter: "blur(12px)",
    WebkitBackdropFilter: "blur(12px)",
  };
}
