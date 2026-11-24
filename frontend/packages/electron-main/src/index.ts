export type { WindowThemeColors } from "./windowTheme.ts";
export {
  isHexColor,
  readSavedWindowTheme,
  saveWindowTheme,
} from "./windowTheme.ts";
export {
  isExactAppSenderUrl,
  rejectNonAppSender,
  type AppSenderGuardOptions,
  type ExactAppSenderInput,
} from "./appSender.ts";
export { isAbsolutePathArg } from "./pathArg.ts";
export type {
  FsEntryResult,
  FsErr,
  FsOk,
  FsResult,
  FsRootResult,
} from "./fsIpcTypes.ts";
export {
  registerFileHandlers,
  type RegisterFileHandlersOptions,
} from "./registerFileHandlers.ts";
export {
  createAutostart,
  type AutostartOptions,
  type AutostartStatus,
} from "./autostart.ts";
export {
  buildDesktopExecLine,
  buildDesktopFileText,
  desktopExecMatches,
  isDesktopAutostartEnabled,
  parseDesktopExec,
  quoteDesktopExecPath,
} from "./autostartDesktop.ts";
