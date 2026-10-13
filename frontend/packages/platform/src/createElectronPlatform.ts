import type { Platform } from "@beamlink/core";

/**
 * Builds a Platform from window.electronAPI, reading session env once.
 */
export async function createElectronPlatform(): Promise<Platform> {
  const sep = window.electronAPI.getSep();
  const homedir = window.electronAPI.getHomedir();

  return {
    env: { sep, homedir },
    getPathForFiles: (files) =>
      Promise.resolve(window.electronAPI.getPathForFiles(files)),
    getRoots: () => Promise.resolve(window.electronAPI.getRoots()),
    readDir: (dirPath) => Promise.resolve(window.electronAPI.readDir(dirPath)),
    mkdir: (dirPath) => Promise.resolve(window.electronAPI.mkdir(dirPath)),
    exists: (dirPath) => Promise.resolve(window.electronAPI.exists(dirPath)),
    onBackendStatus: (cb) =>
      Promise.resolve(window.electronAPI.onBackendStatus(cb)),
    getBackendStatus: () => window.electronAPI.getBackendStatus(),
    setWindowTheme: (colors) => window.electronAPI.setWindowTheme(colors),
  };
}
