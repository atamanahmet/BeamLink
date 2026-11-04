import type { SettingsApi } from "@beamlink/core";

/**
 * Settings stubs. Nexus address lives in NexusSettingsSection + native prefs.
 */
export const settingsApi: SettingsApi = {
  getUploadDir: async () => ({ path: "" }),

  putUploadDir: async () => ({ path: "" }),

  getSpeedCap: async () => ({ value: 0 }),

  putSpeedCap: async () => {},

  getTotalSpeedLimit: async () => ({ value: null }),

  putTotalSpeedLimit: async () => {},

  getResumePolicy: async () => ({ resumePolicy: "OWNER_ONLY" as const }),

  putResumePolicy: async () => {},

  getMinFreeSpace: async () => ({ value: 1073741824 }),

  putMinFreeSpace: async () => {},

  openUploadsFolder: async () => {},

  openLogs: async () => {},

  changeCredentials: async () => {
    throw new Error("Credentials are not used on Android");
  },
};
