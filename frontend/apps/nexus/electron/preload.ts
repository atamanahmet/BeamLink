import { contextBridge, ipcRenderer, webUtils } from "electron";

type FsResult<T> = { ok: true; data: T } | { ok: false; code: string };

/**
 * Unwraps an fs IPC result or throws the error code.
 */
async function unwrapFs<T>(resultPromise: Promise<FsResult<T>>): Promise<T> {
  const result = await resultPromise;
  if (!result.ok) {
    throw new Error(result.code);
  }
  return result.data;
}

contextBridge.exposeInMainWorld("electronAPI", {
  selectFilesAndDirectories: () =>
    ipcRenderer.invoke("select-files-and-directories"),

  getPathForFiles: (files: File[]) => {
    return files.map((f) => webUtils.getPathForFile(f)).filter(Boolean);
  },

  getRoots: () => unwrapFs(ipcRenderer.invoke("fs:get-roots")),

  getHomedir: () => unwrapFs(ipcRenderer.invoke("fs:get-homedir")),

  getSep: (): string => (process.platform === "win32" ? "\\" : "/"),

  readDir: (dirPath: string) =>
    unwrapFs(ipcRenderer.invoke("fs:read-dir", dirPath)),

  mkdir: async (dirPath: string): Promise<void> => {
    await unwrapFs(ipcRenderer.invoke("fs:mkdir", dirPath));
  },

  exists: async (dirPath: string): Promise<boolean> => {
    try {
      return await unwrapFs(ipcRenderer.invoke("fs:exists", dirPath));
    } catch {
      return false;
    }
  },

  onBackendStatus: (
    cb: (data: {
      status: string;
      backendUrl?: string;
      message?: string;
      attempt?: number;
      max?: number;
    }) => void,
  ) => {
    ipcRenderer.on("backend-status", (_e, data) => cb(data));
  },

  onBackendLog: (cb: (line: string) => void) => {
    ipcRenderer.on("backend-log", (_e, line) => cb(line));
  },

  getBackendStatus: () => ipcRenderer.invoke("get-backend-status"),

  setWindowTheme: (colors: { background: string; foreground: string }) =>
    ipcRenderer.invoke("window:set-theme", colors),

  showWindow: () => ipcRenderer.invoke("window:show"),

  saveAuthToken: (token: string) => ipcRenderer.invoke("auth:save-token", token),

  loadAuthToken: () => ipcRenderer.invoke("auth:load-token"),

  clearAuthToken: () => ipcRenderer.invoke("auth:clear-token"),
});
