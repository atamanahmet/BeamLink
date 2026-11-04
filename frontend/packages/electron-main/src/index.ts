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
