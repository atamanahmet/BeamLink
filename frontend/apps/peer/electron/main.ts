import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Tray,
  Menu,
  nativeImage,
  session,
} from "electron";
import {
  isExactAppSenderUrl,
  isHexColor,
  readSavedWindowTheme,
  registerFileHandlers,
  rejectNonAppSender,
  saveWindowTheme,
  type AppSenderGuardOptions,
} from "@beamlink/electron-main";
import { spawn, ChildProcess } from "child_process";
import { randomBytes } from "crypto";
import path from "path";
import fs from "fs";
import { pathToFileURL } from "url";

const DEFAULT_WINDOW_THEME = {
  background: "#121212",
  foreground: "#f5f5f5",
};

const PACKAGED = process.env.NODE_ENV !== "development";
const DEV_ORIGIN = "http://localhost:5174";
const APP_INDEX_URL = pathToFileURL(
  path.join(__dirname, "../dist/index.html"),
).href;
const APP_SENDER: AppSenderGuardOptions = {
  packaged: PACKAGED,
  appIndexUrl: APP_INDEX_URL,
  devOrigin: DEV_ORIGIN,
  caseInsensitivePath: process.platform === "win32",
};

/**
 * Per-launch UI secret for local backend API calls.
 */
const DEVICE_UI_SECRET = randomBytes(32).toString("hex");

registerFileHandlers(ipcMain, APP_SENDER);

/**
 * Injects the UI secret on requests to the local device or Vite proxy.
 */
function installUiSecretHeader(): void {
  const filter = {
    urls: [
      "http://localhost:9090/*",
      "http://127.0.0.1:9090/*",
      "http://localhost:5174/*",
      "http://127.0.0.1:5174/*",
    ],
  };
  session.defaultSession.webRequest.onBeforeSendHeaders(filter, (details, callback) => {
    const requestHeaders = { ...details.requestHeaders };
    requestHeaders["X-UI-Secret"] = DEVICE_UI_SECRET;
    callback({ requestHeaders });
  });
}

let mainWindow: BrowserWindow | null = null;
let backendProcess: ChildProcess | null = null;
let tray: Tray | null = null;
let isQuitting = false;
let resourcesDir: string = "";
let runtimeDir: string = "";
let jreDir: string = "";
let BACKEND_PORT: number = 9090;

let backendState: { status: string; backendUrl?: string; message?: string } = {
  status: "starting",
};

ipcMain.handle("get-backend-status", (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  return backendState;
});

function readRuntimePort(resourcesDir: string, defaultPort: number): number {
  const runtimePath = path.join(resourcesDir, "peer-runtime.json");
  try {
    const raw = fs.readFileSync(runtimePath, "utf-8");
    const parsed = JSON.parse(raw);
    const port = parseInt(parsed.port, 10);
    console.log(`[backend] port from peer-runtime.json: ${port}`);
    return port;
  } catch {
    console.log(
      `[backend] peer-runtime.json not found, using default port: ${defaultPort}`,
    );
    return defaultPort;
  }
}

async function waitForRuntimeJson(
  resourcesDir: string,
  defaultPort: number,
  retries = 30,
  intervalMs = 500,
): Promise<number> {
  const runtimePath = path.join(resourcesDir, "peer-runtime.json");
  for (let i = 0; i < retries; i++) {
    if (fs.existsSync(runtimePath)) {
      return readRuntimePort(resourcesDir, defaultPort);
    }
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  console.log(
    `[backend] peer-runtime.json never appeared, using default: ${defaultPort}`,
  );
  return defaultPort;
}

function getIconPath() {
  const base = path.join(__dirname, "../assets");

  if (process.platform === "win32") {
    const ico = path.join(base, "icon.ico");
    return fs.existsSync(ico) ? ico : path.join(base, "icon.png");
  }

  if (process.platform === "darwin") {
    const icns = path.join(base, "icon.icns");
    return fs.existsSync(icns) ? icns : path.join(base, "icon.png");
  }

  return path.join(base, "icon.png");
}

async function waitForBackend(
  url: string,
  retries = 60,
  intervalMs = 1000,
): Promise<boolean> {
  for (let i = 0; i < retries; i++) {
    try {
      await fetch(url, {
        headers: { "X-UI-Secret": DEVICE_UI_SECRET },
      });
      return true;
    } catch {
      mainWindow?.webContents.send("backend-status", {
        status: "starting",
        attempt: i + 1,
        max: retries,
      });
      await new Promise((res) => setTimeout(res, intervalMs));
    }
  }
  return false;
}

function killBackend() {
  if (backendProcess && !backendProcess.killed) {
    backendProcess.kill();
    backendProcess = null;
  }
}

function startBackend() {
  const javaBinary = process.platform === "win32" ? "java.exe" : "java";

  const javaPath = path.join(jreDir, "bin", javaBinary);
  const jarPath = path.join(resourcesDir, "backend", "beamlink-peer.jar");

  if (!fs.existsSync(javaPath)) {
    console.error("[backend] Java not found at:", javaPath);
    mainWindow?.webContents.send("backend-status", {
      status: "error",
      message: `Java not found at: ${javaPath}`,
    });
    return;
  }

  if (!fs.existsSync(jarPath)) {
    console.error("[backend] JAR not found at:", jarPath);
    mainWindow?.webContents.send("backend-status", {
      status: "error",
      message: `JAR not found at: ${jarPath}`,
    });
    return;
  }

  backendProcess = spawn(
    javaPath,
    [`-Dbeamlink.runtime.dir=${runtimeDir}`, "-jar", jarPath],
    {
      cwd: path.join(resourcesDir, "backend"),
      stdio: ["ignore", "pipe", "pipe"],
      env: {
        ...process.env,
        PEER_UI_SECRET: DEVICE_UI_SECRET,
      },
    },
  );

  backendProcess.stdout?.on("data", (d) =>
    console.log("[backend]", d.toString().trimEnd()),
  );
  backendProcess.stderr?.on("data", (d) =>
    console.error("[backend]", d.toString().trimEnd()),
  );
  backendProcess.on("exit", (code) => {
    console.log(`[backend] exited with code ${code}`);
    if (!isQuitting) {
      mainWindow?.webContents.send("backend-status", {
        status: "error",
        message: `Backend stopped unexpectedly (code ${code}).`,
      });
    }
  });
}

function createTray() {
  const icon = nativeImage.createFromPath(getIconPath());
  tray = new Tray(icon);
  tray.setToolTip("BeamLink Peer");
  tray.setContextMenu(
    Menu.buildFromTemplate([
      {
        label: "Open",
        click: () => {
          mainWindow?.show();
          mainWindow?.focus();
          if (process.platform === "darwin") app.dock?.show();
        },
      },
      { type: "separator" },
      {
        label: "Quit",
        click: () => {
          isQuitting = true;
          app.quit();
        },
      },
    ]),
  );
  tray.on("click", () => {
    if (mainWindow?.isVisible()) {
      mainWindow.hide();
      if (process.platform === "darwin") app.dock?.hide();
    } else {
      mainWindow?.show();
      mainWindow?.focus();
      if (process.platform === "darwin") app.dock?.show();
    }
  });
}

/**
 * Returns the path of the saved window theme file.
 */
function windowThemePath(): string {
  return path.join(app.getPath("userData"), "window-theme.json");
}

function createWindow() {
  const theme = readSavedWindowTheme(windowThemePath(), DEFAULT_WINDOW_THEME);
  mainWindow = new BrowserWindow({
    show: false,
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    icon: getIconPath(),
    backgroundColor: theme.background,
    titleBarStyle: "hidden",
    titleBarOverlay: {
      color: theme.background,
      symbolColor: theme.foreground,
      height: 40,
    },
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (!isExactAppSenderUrl({ ...APP_SENDER, senderUrl: url })) {
      event.preventDefault();
    }
  });

  if (process.env.NODE_ENV === "development") {
    mainWindow.loadURL(DEV_ORIGIN);
  } else {
    mainWindow.loadFile(path.join(__dirname, "../dist/index.html"));
  }
  mainWindow.on("close", (e) => {
    if (!isQuitting) {
      e.preventDefault();
      mainWindow?.hide();
      if (process.platform === "darwin") app.dock?.hide();
    }
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!mainWindow) {
      return;
    }
    if (mainWindow.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow.show();
    mainWindow.focus();
    if (process.platform === "darwin") {
      app.dock?.show();
    }
  });

app.whenReady().then(async () => {
  installUiSecretHeader();

  resourcesDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../../resources/peer")
      : process.resourcesPath;

  jreDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../../resources/jre")
      : path.join(process.resourcesPath, "jre");

  runtimeDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../../resources/peer")
      : path.join(app.getPath("appData"), "BeamLink", "Peer");

  const runtimeSubdirs = ["data/database", "data/uploads", "logs"];
  for (const dir of runtimeSubdirs) {
    fs.mkdirSync(path.join(runtimeDir, dir), { recursive: true });
  }

  if (process.platform === "darwin") {
    app.dock?.setIcon(path.join(__dirname, "../assets/icon.png"));
  }

  createWindow();
  createTray();

  // Show window immediately so StartupScreen is visible while backend starts
  mainWindow?.once("ready-to-show", () => {
    mainWindow?.show();
    mainWindow?.focus();
    if (process.platform === "darwin") app.dock?.show();
  });

  startBackend();

  BACKEND_PORT = await waitForRuntimeJson(runtimeDir, 9090);

  const ok = await waitForBackend(
    `http://localhost:${BACKEND_PORT}/actuator/health`,
  );

  backendState = ok
    ? { status: "ready", backendUrl: `http://localhost:${BACKEND_PORT}` }
    : { status: "error", message: "Backend did not respond after 60 seconds." };

  mainWindow?.webContents.send("backend-status", backendState);
});
}

app.on("window-all-closed", () => {
  // tray keeps the app alive
});

app.on("before-quit", () => {
  isQuitting = true;
  killBackend();
});

process.on("exit", () => {
  killBackend();
});

process.on("SIGINT", () => {
  isQuitting = true;
  killBackend();
  process.exit(0);
});

process.on("SIGTERM", () => {
  isQuitting = true;
  killBackend();
  process.exit(0);
});

ipcMain.handle("select-file", async (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ["openFile"],
  });
  return result.filePaths[0] ?? null;
});

ipcMain.handle("select-directory", async (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ["openDirectory"],
  });
  return result.filePaths[0] ?? null;
});

ipcMain.handle("select-files", async (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ["openFile", "multiSelections"],
  });
  return result.filePaths;
});

ipcMain.handle("select-directories", async (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ["openDirectory", "multiSelections"],
  });
  return result.filePaths;
});

ipcMain.handle("select-files-and-directories", async (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const result = await dialog.showOpenDialog(mainWindow!, {
    properties: ["openFile", "openDirectory", "multiSelections"],
  });
  return result.filePaths;
});

ipcMain.handle(
  "window:set-theme",
  async (
    event,
    colors: { background?: unknown; foreground?: unknown },
  ) => {
    const bad = rejectNonAppSender(event, APP_SENDER);
    if (bad) return bad;
    if (
      typeof colors?.background !== "string" ||
      typeof colors?.foreground !== "string" ||
      !isHexColor(colors.background) ||
      !isHexColor(colors.foreground)
    ) {
      return { ok: false, code: "INVALID_THEME" };
    }
    const win = BrowserWindow.fromWebContents(event.sender);
    if (!win) {
      return { ok: false, code: "INVALID_SENDER" };
    }
    const next = {
      background: colors.background,
      foreground: colors.foreground,
    };
    win.setBackgroundColor(next.background);
    if (typeof win.setTitleBarOverlay === "function") {
      try {
        win.setTitleBarOverlay({
          color: next.background,
          symbolColor: next.foreground,
          height: 40,
        });
      } catch (error) {
        console.error("[theme] setTitleBarOverlay failed:", error);
      }
    }
    saveWindowTheme(windowThemePath(), next);
  },
);

ipcMain.handle("window:show", async (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const win = BrowserWindow.fromWebContents(event.sender) ?? mainWindow;
  if (!win) {
    return { ok: false, code: "INVALID_SENDER" };
  }
  win.show();
  win.focus();
  if (process.platform === "darwin") app.dock?.show();
  return { ok: true };
});
