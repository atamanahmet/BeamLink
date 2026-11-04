import { apiClient } from "./client";

export type DeviceStatus = {
  deviceId: string | null;
  name: string;
  nexusUrl: string;
  status: string;
  uploadDirectory: string;
  port: number;
  fileCount: number;
  hint?: string;
  fingerprint?: string;
  state?: string;
  pinSet?: boolean;
};

/**
 * Loads device status for the info screen.
 */
export async function getDeviceStatus(): Promise<DeviceStatus> {
  return (await apiClient.get<DeviceStatus>("/status")).data;
}

/**
 * Asks nexus to rename this device.
 */
export async function requestRename(
  deviceId: string,
  newName: string,
): Promise<void> {
  await apiClient.post(`/${deviceId}/rename`, { name: newName });
}
