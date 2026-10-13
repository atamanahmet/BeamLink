import { createContext, useContext, type ReactNode } from "react";
import type { Platform } from "@beamlink/core";

const PlatformContext = createContext<Platform | null>(null);

/**
 * Provides a Platform instance to the renderer tree.
 */
export function PlatformProvider({
  platform,
  children,
}: {
  platform: Platform;
  children: ReactNode;
}) {
  return (
    <PlatformContext.Provider value={platform}>
      {children}
    </PlatformContext.Provider>
  );
}

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
