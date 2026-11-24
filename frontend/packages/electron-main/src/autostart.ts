import fs from "fs";
import os from "os";
import path from "path";
import {
  buildDesktopFileText,
  desktopExecMatches,
  isDesktopAutostartEnabled,
} from "./autostartDesktop.ts";

export type AutostartOptions = {
  appId: string;
  displayName: string;
  desktopFileName: string;
};

export type AutostartStatus = {
  supported: boolean;
  enabled: boolean;
};

type ElectronAppLike = {
  isPackaged: boolean;
  setLoginItemSettings: (settings: {
    openAtLogin: boolean;
    args?: string[];
  }) => void;
  getLoginItemSettings: (options?: { args?: string[] }) => {
    openAtLogin: boolean;
  };
};

type AutostartEnv = {
  platform: NodeJS.Platform;
  appImage: string | undefined;
  homedir: () => string;
};

/**
 * Builds the Linux autostart desktop file path.
 */
function linuxDesktopPath(
  desktopFileName: string,
  homedir: () => string,
): string {
  return path.join(homedir(), ".config", "autostart", desktopFileName);
}

/**
 * Creates an autostart controller for one Electron app.
 */
export function createAutostart(
  app: ElectronAppLike,
  opts: AutostartOptions,
  env: AutostartEnv = {
    platform: process.platform,
    appImage: process.env.APPIMAGE,
    homedir: () => os.homedir(),
  },
) {
  /**
   * True when this platform and package layout can use autostart.
   */
  function isSupported(): boolean {
    if (!app.isPackaged) {
      return false;
    }
    if (env.platform === "win32") {
      return true;
    }
    if (env.platform === "linux") {
      return Boolean(env.appImage);
    }
    return false;
  }

  /**
   * Rewrites the Linux desktop file when Exec is stale.
   */
  function syncLinuxDesktopExec(): void {
    if (!isSupported() || env.platform !== "linux" || !env.appImage) {
      return;
    }
    const file = linuxDesktopPath(opts.desktopFileName, env.homedir);
    if (!fs.existsSync(file)) {
      return;
    }
    const content = fs.readFileSync(file, "utf-8");
    if (!isDesktopAutostartEnabled(content)) {
      return;
    }
    if (desktopExecMatches(content, env.appImage)) {
      return;
    }
    fs.writeFileSync(
      file,
      buildDesktopFileText({
        name: opts.displayName,
        appImagePath: env.appImage,
      }),
      "utf-8",
    );
  }

  /**
   * Reads whether autostart is supported and enabled.
   */
  function getAutostart(): AutostartStatus {
    if (!isSupported()) {
      return { supported: false, enabled: false };
    }
    if (env.platform === "win32") {
      const settings = app.getLoginItemSettings({ args: ["--hidden"] });
      return { supported: true, enabled: settings.openAtLogin };
    }
    const file = linuxDesktopPath(opts.desktopFileName, env.homedir);
    if (!fs.existsSync(file)) {
      return { supported: true, enabled: false };
    }
    const content = fs.readFileSync(file, "utf-8");
    return {
      supported: true,
      enabled: isDesktopAutostartEnabled(content),
    };
  }

  /**
   * Enables or disables autostart for this app.
   */
  function setAutostart(enabled: boolean): AutostartStatus {
    if (!isSupported()) {
      return { supported: false, enabled: false };
    }
    if (env.platform === "win32") {
      app.setLoginItemSettings({
        openAtLogin: enabled,
        args: ["--hidden"],
      });
      return getAutostart();
    }
    const file = linuxDesktopPath(opts.desktopFileName, env.homedir);
    if (!enabled) {
      if (fs.existsSync(file)) {
        fs.unlinkSync(file);
      }
      return { supported: true, enabled: false };
    }
    if (!env.appImage) {
      return { supported: false, enabled: false };
    }
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(
      file,
      buildDesktopFileText({
        name: opts.displayName,
        appImagePath: env.appImage,
      }),
      "utf-8",
    );
    return { supported: true, enabled: true };
  }

  return {
    getAutostart,
    setAutostart,
    syncLinuxDesktopExec,
  };
}
