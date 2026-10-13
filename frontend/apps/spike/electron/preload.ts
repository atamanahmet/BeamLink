import { contextBridge, webUtils } from "electron";

contextBridge.exposeInMainWorld("electronAPI", {
  getPathForFiles: (files: File[]) => {
    return files.map((f: File) => webUtils.getPathForFile(f) || "");
  },
});
