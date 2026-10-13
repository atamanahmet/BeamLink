/// <reference path="./windowElectronApi.d.ts" />

export type { Platform } from "@beamlink/core";
export { createElectronPlatform } from "./createElectronPlatform.ts";
export { PlatformProvider, usePlatform } from "./PlatformContext.tsx";
