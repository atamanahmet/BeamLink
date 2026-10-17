import { useEffect, useState } from "react";
import type { SettingsApi } from "@beamlink/core";
import { changeCredentialsAction } from "./credentialsActions.ts";
import { openLogsAction } from "./logActions.ts";
import { saveSpeedCapAction } from "./speedCapActions.ts";
import { saveUploadDirAction } from "./uploadDirActions.ts";
import type {
  CredentialsState,
  LogState,
  SpeedCapState,
  UploadDirState,
} from "./types.ts";

type UseSettingsControllerArgs = {
  settingsApi: SettingsApi;
  onLogout: () => void;
};

/**
 * Owns settings form state and api calls.
 */
export function useSettingsController({
  settingsApi,
  onLogout,
}: UseSettingsControllerArgs) {
  const [uploadDir, setUploadDir] = useState<UploadDirState>({
    current: "",
    save: "idle",
    error: null,
  });
  const [log, setLog] = useState<LogState>({ opening: false, error: null });
  const [credentials, setCredentials] = useState<CredentialsState>({
    currentPassword: "",
    newUsername: "",
    newPassword: "",
    confirmNewPassword: "",
    save: "idle",
    error: null,
  });
  const [speedCap, setSpeedCap] = useState<SpeedCapState>({
    value: "",
    save: "idle",
    error: null,
  });
  const [folderBrowserOpen, setFolderBrowserOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void settingsApi
      .getUploadDir()
      .then((data) => {
        if (!cancelled) {
          setUploadDir((prev) => ({ ...prev, current: data.path }));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUploadDir((prev) => ({
            ...prev,
            error: "Failed to load upload directory.",
          }));
        }
      });

    void settingsApi
      .getSpeedCap()
      .then((data) => {
        if (!cancelled) {
          setSpeedCap((prev) => ({
            ...prev,
            value: data.value == null ? "" : String(data.value),
          }));
        }
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [settingsApi]);

  const openFolder = async () => {
    try {
      await settingsApi.openUploadsFolder();
    } catch {
      // Folder open is best effort; ignore failures.
    }
  };

  return {
    uploadDir,
    log,
    credentials,
    speedCap,
    folderBrowserOpen,
    setFolderBrowserOpen,
    setSpeedCap,
    setCredentials,
    saveUploadDir: (path: string) =>
      saveUploadDirAction(settingsApi, path, setUploadDir),
    openFolder,
    openLogs: () => openLogsAction(settingsApi, setLog),
    saveSpeedCap: () => saveSpeedCapAction(settingsApi, speedCap, setSpeedCap),
    changeCredentials: () =>
      changeCredentialsAction(
        settingsApi,
        credentials,
        setCredentials,
        onLogout,
      ),
  };
}
