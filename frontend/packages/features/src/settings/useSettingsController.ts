import { useEffect, useState } from "react";
import {
  bytesToGib,
  problemFromUnknown,
  type SettingsApi,
} from "@beamlink/core";
import { changeCredentialsAction } from "./credentialsActions.ts";
import { openLogsAction } from "./logActions.ts";
import { saveMinFreeSpaceAction } from "./minFreeSpaceActions.ts";
import { saveSpeedCapAction } from "./speedCapActions.ts";
import { saveUploadDirAction } from "./uploadDirActions.ts";
import type {
  CredentialsState,
  LogState,
  MinFreeSpaceState,
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
  const [log, setLog] = useState<LogState>({
    opening: false,
    error: null,
    correlationId: null,
  });
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
  const [minFreeSpace, setMinFreeSpace] = useState<MinFreeSpaceState>({
    value: "1",
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
      .catch((err: unknown) => {
        if (!cancelled) {
          const problem = problemFromUnknown(err);
          setUploadDir((prev) => ({
            ...prev,
            error: problem.detail ?? "Failed to load upload directory.",
            correlationId: problem.correlationId,
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

    void settingsApi
      .getMinFreeSpace()
      .then((data) => {
        if (!cancelled) {
          setMinFreeSpace((prev) => ({
            ...prev,
            value: String(bytesToGib(data.value)),
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
    minFreeSpace,
    folderBrowserOpen,
    setFolderBrowserOpen,
    setSpeedCap,
    setMinFreeSpace,
    setCredentials,
    saveUploadDir: (path: string) =>
      saveUploadDirAction(settingsApi, path, setUploadDir),
    openFolder,
    openLogs: () => openLogsAction(settingsApi, setLog),
    saveSpeedCap: () => saveSpeedCapAction(settingsApi, speedCap, setSpeedCap),
    saveMinFreeSpace: () =>
      saveMinFreeSpaceAction(settingsApi, minFreeSpace, setMinFreeSpace),
    resetMinFreeSpace: () => {
      setMinFreeSpace((prev) => ({
        ...prev,
        value: "1",
        error: null,
      }));
      void saveMinFreeSpaceAction(
        settingsApi,
        { value: "1", save: "idle", error: null, correlationId: null },
        setMinFreeSpace,
      );
    },
    changeCredentials: () =>
      changeCredentialsAction(
        settingsApi,
        credentials,
        setCredentials,
        onLogout,
      ),
  };
}
