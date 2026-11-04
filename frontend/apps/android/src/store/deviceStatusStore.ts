import { create } from "zustand";
import type { DeviceStatus } from "../api/deviceApi";

type DeviceStatusState = {
  deviceStatus: DeviceStatus | null;
  setDeviceStatus: (status: DeviceStatus | null) => void;
};

/**
 * Live device status from /status and SSE.
 */
export const useDeviceStatusStore = create<DeviceStatusState>((set) => ({
  deviceStatus: null,
  setDeviceStatus: (deviceStatus) => set({ deviceStatus }),
}));
