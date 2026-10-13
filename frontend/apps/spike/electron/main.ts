import { app, BrowserWindow } from "electron";
import path from "path";
import { runSelfTest } from "./selftest";

let mainWindow: BrowserWindow | null = null;

/**
 * Creates the single BrowserWindow and loads the renderer.
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 960,
    height: 640,
    minWidth: 320,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  if (process.env.NODE_ENV === "development") {
    mainWindow.loadURL("http://localhost:5175");
  } else {
    mainWindow.loadFile(path.join(__dirname, "../dist/index.html"));
  }
}

app.whenReady().then(() => {
  createWindow();
  if (process.env.SPIKE_SELFTEST === "1" && mainWindow) {
    mainWindow.webContents.once("did-finish-load", () => {
      void runSelfTest(mainWindow!);
    });
  }
});

app.on("window-all-closed", () => {
  app.quit();
});
