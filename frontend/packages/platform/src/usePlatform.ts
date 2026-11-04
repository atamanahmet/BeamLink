import { useContext } from "react";
import type { Platform } from "@beamlink/core";
import { PlatformContext } from "./platformContext.ts";

/**
 * Returns the Platform from the nearest PlatformProvider.
 */
export function usePlatform(): Platform {
  const platform = useContext(PlatformContext);
  if (!platform) {
    throw new Error("usePlatform must be used within a PlatformProvider");
  }
  return platform;
}
