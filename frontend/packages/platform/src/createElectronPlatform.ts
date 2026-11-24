import type { Platform } from "@beamlink/core";

/**
 * Builds a Platform from window.electronAPI, reading session env once.
 * Homedir falls back to the drive or root so sidebar shortcuts still mount.
 */
export async function createElectronPlatform(): Promise<Platform> {
  const sep = window.electronAPI.getSep();
  let homedir: string;
  try {
    homedir = await window.electronAPI.getHomedir();
  } catch {
    homedir = sep === "\\" ? "C:\\" : "/";
    console.warn("[platform] homedir unavailable, using", homedir);
  }

  const hasAutostart =
    typeof window.electronAPI.getAutostart === "function" &&
    typeof window.electronAPI.setAutostart === "function";

  return {
    env: { sep, homedir },
    capabilities: {
      themeEdit: true,
      themeAdd: true,
      transferHistory: true,
      transferLogs: true,
      warp: true,
      autostart: hasAutostart,
    },
    getPathForFiles: (files) =>
      Promise.resolve(window.electronAPI.getPathForFiles(files)),
    getRoots: () => window.electronAPI.getRoots(),
    readDir: (dirPath) => window.electronAPI.readDir(dirPath),
    mkdir: (dirPath) => window.electronAPI.mkdir(dirPath),
    exists: (dirPath) => window.electronAPI.exists(dirPath),
    onBackendStatus: (cb) =>
      Promise.resolve(window.electronAPI.onBackendStatus(cb)),
    getBackendStatus: () => window.electronAPI.getBackendStatus(),
    setWindowTheme: (colors) => window.electronAPI.setWindowTheme(colors),
    getAutostart: () =>
      hasAutostart
        ? window.electronAPI.getAutostart!()
        : Promise.resolve({ supported: false, enabled: false }),
    setAutostart: (enabled) =>
      hasAutostart
        ? window.electronAPI.setAutostart!(enabled)
        : Promise.resolve({ supported: false, enabled: false }),
  };
}
