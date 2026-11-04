import fs from "fs";
import os from "os";
import path from "path";
import {
  rejectNonAppSender,
  type AppSenderGuardOptions,
} from "./appSender.ts";
import type { FsEntryResult, FsResult, FsRootResult } from "./fsIpcTypes.ts";
import { isAbsolutePathArg } from "./pathArg.ts";

type InvokeEvent = {
  senderFrame?: { url: string } | null;
  sender?: { mainFrame?: unknown } | null;
};

type IpcMainLike = {
  handle: (
    channel: string,
    listener: (event: InvokeEvent, ...args: unknown[]) => unknown,
  ) => void;
};

export type RegisterFileHandlersOptions = AppSenderGuardOptions;

/**
 * Maps a node fs error to a short code string.
 */
function fsErrorCode(error: unknown): string {
  const code =
    error && typeof error === "object" && "code" in error
      ? String((error as { code: unknown }).code)
      : "";
  if (code === "EEXIST") return "EXISTS";
  if (code === "ENOENT") return "NOT_FOUND";
  if (code === "EACCES" || code === "EPERM") return "PERMISSION";
  return "IO_ERROR";
}

/**
 * Registers shared file browser IPC handlers on ipcMain.
 */
export function registerFileHandlers(
  ipcMain: IpcMainLike,
  options: RegisterFileHandlersOptions,
): void {
  ipcMain.handle("fs:get-homedir", (event) => {
    const bad = rejectNonAppSender(event, options);
    if (bad) return bad;
    return { ok: true, data: os.homedir() } satisfies FsResult<string>;
  });

  ipcMain.handle("fs:get-roots", (event) => {
    const bad = rejectNonAppSender(event, options);
    if (bad) return bad;

    if (process.platform === "win32") {
      const roots: FsRootResult[] = [];
      for (const letter of "ABCDEFGHIJKLMNOPQRSTUVWXYZ") {
        const fullPath = `${letter}:\\`;
        try {
          fs.accessSync(fullPath);
          roots.push({
            name: `${letter}:`,
            fullPath,
            isDirectory: true,
            locked: false,
            hidden: false,
          });
        } catch {
          // skip missing drive
        }
      }
      return { ok: true, data: roots } satisfies FsResult<FsRootResult[]>;
    }

    return {
      ok: true,
      data: [
        {
          name: "/",
          fullPath: "/",
          isDirectory: true,
          locked: false,
          hidden: false,
        },
      ],
    } satisfies FsResult<FsRootResult[]>;
  });

  ipcMain.handle("fs:read-dir", (event, dirPath) => {
    const bad = rejectNonAppSender(event, options);
    if (bad) return bad;
    if (!isAbsolutePathArg(dirPath)) {
      return { ok: false, code: "INVALID_PATH" } satisfies FsResult<never>;
    }

    try {
      const raw = fs.readdirSync(dirPath, { withFileTypes: true });
      const entries: FsEntryResult[] = raw.map((entry) => {
        const fullPath = path.join(dirPath, entry.name);
        const isDirectory = entry.isDirectory();
        const hidden = entry.name.startsWith(".");

        let locked = false;
        try {
          fs.accessSync(fullPath, fs.constants.R_OK);
        } catch {
          locked = true;
        }

        let size: number | undefined;
        if (!isDirectory && !locked) {
          try {
            size = fs.statSync(fullPath).size;
          } catch {
            locked = true;
          }
        }

        return { name: entry.name, fullPath, isDirectory, size, locked, hidden };
      });
      return { ok: true, data: entries } satisfies FsResult<FsEntryResult[]>;
    } catch (error) {
      return { ok: false, code: fsErrorCode(error) } satisfies FsResult<never>;
    }
  });

  ipcMain.handle("fs:mkdir", (event, dirPath) => {
    const bad = rejectNonAppSender(event, options);
    if (bad) return bad;
    if (!isAbsolutePathArg(dirPath)) {
      return { ok: false, code: "INVALID_PATH" } satisfies FsResult<never>;
    }

    try {
      fs.mkdirSync(dirPath, { recursive: false });
      return { ok: true, data: null } satisfies FsResult<null>;
    } catch (error) {
      return { ok: false, code: fsErrorCode(error) } satisfies FsResult<never>;
    }
  });

  ipcMain.handle("fs:exists", (event, dirPath) => {
    const bad = rejectNonAppSender(event, options);
    if (bad) return bad;
    if (!isAbsolutePathArg(dirPath)) {
      return { ok: false, code: "INVALID_PATH" } satisfies FsResult<never>;
    }
    return {
      ok: true,
      data: fs.existsSync(dirPath),
    } satisfies FsResult<boolean>;
  });
}
