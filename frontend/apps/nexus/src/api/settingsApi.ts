import type { SettingsApi } from "@beamlink/core";
import { apiClient } from "./client";

/**
 * Nexus settings HTTP API.
 */
export const settingsApi: SettingsApi = {
  getUploadDir: async () =>
    (await apiClient.get<{ path: string }>("/storage/upload-dir")).data,

  putUploadDir: async (path) =>
    (
      await apiClient.put<{ path: string; error?: string }>(
        "/storage/upload-dir",
        { path },
      )
    ).data,

  getSpeedCap: async () =>
    (await apiClient.get<{ value: number }>("/settings/speed-cap")).data,

  putSpeedCap: async (value) => {
    await apiClient.put("/settings/speed-cap", { value });
  },

  getTotalSpeedLimit: async () =>
    (await apiClient.get<{ value: number | null }>("/settings/total-speed-limit"))
      .data,

  putTotalSpeedLimit: async (value) => {
    await apiClient.put("/settings/total-speed-limit", { value });
  },

  getResumePolicy: async () =>
    (
      await apiClient.get<{ resumePolicy: "OWNER_ONLY" | "ANYONE" }>(
        "/settings/resume-policy",
      )
    ).data,

  putResumePolicy: async (value) => {
    await apiClient.put("/settings/resume-policy", { resumePolicy: value });
  },

  openUploadsFolder: async () => {
    await apiClient.post("/storage/open-uploads-folder");
  },

  openLogs: async () => {
    await apiClient.post("/storage/open-logs");
  },

  changeCredentials: async (req) => {
    await apiClient.post("/auth/change-credentials", req);
  },
};
