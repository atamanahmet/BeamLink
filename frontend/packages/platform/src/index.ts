import "./windowElectronApi.d.ts";

export type {
  Platform,
  PlatformCapabilities,
  SharedFileItem,
} from "@beamlink/core";
export { createElectronPlatform } from "./createElectronPlatform.ts";
export { PlatformProvider } from "./PlatformContext.tsx";
export { usePlatform } from "./usePlatform.ts";
