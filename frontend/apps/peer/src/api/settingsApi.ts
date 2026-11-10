import type { DeviceReRegisterResult, SettingsApi } from "@beamlink/core";
import type { NexusTlsPinAdapter } from "@beamlink/features";
import { apiClient } from "./client";

/**
 * Peer settings HTTP API.
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

  getResumePolicy: async () => ({ resumePolicy: "OWNER_ONLY" as const }),

  putResumePolicy: async () => {},

  getMinFreeSpace: async () =>
    (await apiClient.get<{ value: number }>("/settings/min-free-space")).data,

  putMinFreeSpace: async (value) => {
    await apiClient.put("/settings/min-free-space", { value });
  },

  getLanAddresses: async () =>
    (
      await apiClient.get<{ addresses: string[]; tlsPort: number }>(
        "/settings/lan-addresses",
      )
    ).data,

  openUploadsFolder: async () => {
    await apiClient.post("/storage/open-uploads-folder");
  },

  openLogs: async () => {
    await apiClient.post("/storage/open-logs");
  },

  changeCredentials: async () => {
    throw new Error("Credentials are not used on the peer app");
  },

  reRegister: async (force) =>
    (
      await apiClient.post<DeviceReRegisterResult>("/reset/re-register", null, {
        params: { force: force === true },
      })
    ).data,

  factoryReset: async (force) => {
    await apiClient.post("/reset/factory", null, {
      params: { force: force === true },
    });
  },
};

/**
 * Peer adapter for nexus TLS first contact. HTTP only here.
 */
export const nexusTlsPinAdapter: NexusTlsPinAdapter = {
  getStatus: async () =>
    (await apiClient.get("/nexus/status")).data,

  probe: async (ip, port) =>
    (await apiClient.post("/nexus/probe", { ip, port })).data,

  confirm: async (ip, port, fingerprint) => {
    await apiClient.post("/nexus/confirm", { ip, port, fingerprint });
  },

  cancel: async () => {},

  clearPin: async () => {
    await apiClient.delete("/nexus/pin");
  },
};
