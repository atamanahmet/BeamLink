import { type ReactNode } from "react";
import type { Platform } from "@beamlink/core";
import { PlatformContext } from "./platformContext.ts";

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
