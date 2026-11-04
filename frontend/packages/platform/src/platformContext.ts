import { createContext } from "react";
import type { Platform } from "@beamlink/core";

export const PlatformContext = createContext<Platform | null>(null);
