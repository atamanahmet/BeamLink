import {
  app,
  BrowserWindow,
  dialog,
  ipcMain,
  Tray,
  Menu,
  nativeImage,
  safeStorage,
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
const DEV_ORIGIN = "http://localhost:5173";
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
const NEXUS_UI_SECRET = randomBytes(32).toString("hex");

registerFileHandlers(ipcMain, APP_SENDER);

/**
 * Injects the UI secret on requests to the local nexus or Vite proxy.
 */
function installUiSecretHeader(): void {
  const filter = {
    urls: [
      "http://localhost:7472/*",
      "http://127.0.0.1:7472/*",
      "http://localhost:5173/*",
      "http://127.0.0.1:5173/*",
    ],
  };
  session.defaultSession.webRequest.onBeforeSendHeaders(filter, (details, callback) => {
    const requestHeaders = { ...details.requestHeaders };
    requestHeaders["X-UI-Secret"] = NEXUS_UI_SECRET;
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
let BACKEND_PORT: number = 7472;

let backendState: { status: string; backendUrl?: string; message?: string } = {
  status: "starting",
};

ipcMain.handle("get-backend-status", (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  return backendState;
});

/**
 * Returns the encrypted admin token file path under userData.
 */
function authTokenPath(): string {
  return path.join(app.getPath("userData"), "admin-token.bin");
}

ipcMain.handle("auth:save-token", (event, token: unknown) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  if (typeof token !== "string" || token.length === 0) {
    return { ok: false as const, code: "INVALID_TOKEN" };
  }
  if (!safeStorage.isEncryptionAvailable()) {
    return { ok: false as const, code: "ENCRYPTION_UNAVAILABLE" };
  }
  fs.writeFileSync(authTokenPath(), safeStorage.encryptString(token));
  return { ok: true as const };
});

ipcMain.handle("auth:load-token", (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const file = authTokenPath();
  if (!fs.existsSync(file)) {
    return { ok: true as const, data: null };
  }
  if (!safeStorage.isEncryptionAvailable()) {
    return { ok: false as const, code: "ENCRYPTION_UNAVAILABLE" };
  }
  try {
    const encrypted = fs.readFileSync(file);
    return { ok: true as const, data: safeStorage.decryptString(encrypted) };
  } catch {
    return { ok: true as const, data: null };
  }
});

ipcMain.handle("auth:clear-token", (event) => {
  const bad = rejectNonAppSender(event, APP_SENDER);
  if (bad) return bad;
  const file = authTokenPath();
  if (fs.existsSync(file)) {
    fs.unlinkSync(file);
  }
  return { ok: true as const };
});

function readRuntimePort(resourcesDir: string, defaultPort: number): number {
  const runtimePath = path.join(resourcesDir, "nexus-runtime.json");
  try {
    const raw = fs.readFileSync(runtimePath, "utf-8");
    const parsed = JSON.parse(raw);
    const port = parseInt(parsed.port, 10);
    console.log(`[backend] port from nexus-runtime.json: ${port}`);
    return port;
  } catch {
    console.log(
      `[backend] nexus-runtime.json not found, using default port: ${defaultPort}`,
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
  const runtimePath = path.join(resourcesDir, "nexus-runtime.json");
  for (let i = 0; i < retries; i++) {
    if (fs.existsSync(runtimePath)) {
      return readRuntimePort(resourcesDir, defaultPort);
    }
    await new Promise((res) => setTimeout(res, intervalMs));
  }
  console.log(
    `[backend] nexus-runtime.json never appeared, using default: ${defaultPort}`,
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
        headers: { "X-UI-Secret": NEXUS_UI_SECRET },
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
  const jarPath = path.join(resourcesDir, "backend", "beamlink-nexus.jar");

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
        NEXUS_UI_SECRET,
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
  tray.setToolTip("BeamLink Nexus");
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
}

app.whenReady().then(async () => {
  installUiSecretHeader();

  resourcesDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../../resources/nexus")
      : process.resourcesPath;

  jreDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../../resources/jre")
      : path.join(process.resourcesPath, "jre");

  runtimeDir =
    process.env.NODE_ENV === "development"
      ? path.resolve(__dirname, "../../../../resources/nexus")
      : path.join(app.getPath("appData"), "BeamLink", "Nexus");

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

  BACKEND_PORT = await waitForRuntimeJson(runtimeDir, 7472);

  try {
    const ok = await waitForBackend(
      `http://localhost:${BACKEND_PORT}/actuator/health`,
    );
    backendState = ok
      ? { status: "ready", backendUrl: `http://localhost:${BACKEND_PORT}` }
      : {
          status: "error",
          message: "Backend did not respond after 60 seconds.",
        };

    mainWindow?.webContents.send("backend-status", backendState);
  } catch (error) {
    dialog.showErrorBox("Backend Startup Failed", String(error));

    app.quit();
  }
});

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
