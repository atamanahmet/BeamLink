import { apiClient } from "./client";
import type { TransferLogEntry } from "@beamlink/core";

export type NexusDevice = {
  id: string;
  name: string;
  ipAddress: string;
  port: number;
  online: boolean;
  state: string;
  requestedName?: string;
  registeredAt: string;
  renameRequestedAt?: string;
  lastSeen?: string;
  fileCount?: number;
  fingerprint?: string;
};

export type AuditLogEntry = {
  id: number;
  occurredAt: string;
  eventType: string;
  outcome: "SUCCESS" | "FAILED";
  sourceAddress: string | null;
  targetDeviceId: string | null;
  detail: string | null;
};

export type NexusStats = {
  tlsFingerprint?: string;
  deviceStats: {
    total: number;
    online: number;
    offline: number;
    pending: number;
    pendingRename: number;
  };
  transferStats: {
    totalTransfers: number;
    totalDataTransferred: number;
  };
};

/**
 * Nexus-only admin endpoints.
 */
export interface NexusAdminApi {
  getStats: () => Promise<NexusStats>;
  getDevices: () => Promise<NexusDevice[]>;
  getApprovedDevices: () => Promise<NexusDevice[]>;
  getPendingDevices: () => Promise<NexusDevice[]>;
  getPendingRenames: () => Promise<NexusDevice[]>;
  getTransferLogs: () => Promise<TransferLogEntry[]>;
  getAuditLog: (page: number, size: number) => Promise<AuditLogEntry[]>;
  approveDevice: (id: string) => Promise<void>;
  rejectDevice: (id: string) => Promise<void>;
  removeDevice: (id: string) => Promise<void>;
  approveRename: (id: string) => Promise<void>;
  rejectRename: (id: string) => Promise<void>;
}

/**
 * Nexus admin HTTP service.
 */
export const nexusAdminApi: NexusAdminApi = {
  getStats: async () =>
    (await apiClient.get<NexusStats>("/admin/dashboard/stats")).data,

  getDevices: async () =>
    (await apiClient.get<NexusDevice[]>("/admin/devices")).data,

  getApprovedDevices: async () =>
    (await apiClient.get<NexusDevice[]>("/admin/devices/approved")).data,

  getPendingDevices: async () =>
    (await apiClient.get<NexusDevice[]>("/admin/devices/pending")).data,

  getPendingRenames: async () =>
    (await apiClient.get<NexusDevice[]>("/admin/devices/rename-pending")).data,

  getTransferLogs: async () =>
    (await apiClient.get<TransferLogEntry[]>("/admin/logs")).data,

  getAuditLog: async (page, size) =>
    (
      await apiClient.get<AuditLogEntry[]>("/admin/audit", {
        params: { page, size },
      })
    ).data,

  approveDevice: async (id) => {
    await apiClient.post(`/admin/devices/${id}/approve`);
  },

  rejectDevice: async (id) => {
    await apiClient.post(`/admin/devices/${id}/reject`);
  },

  removeDevice: async (id) => {
    await apiClient.delete(`/admin/devices/${id}`);
  },

  approveRename: async (id) => {
    await apiClient.post(`/admin/devices/${id}/rename/approve`);
  },

  rejectRename: async (id) => {
    await apiClient.post(`/admin/devices/${id}/rename/reject`);
  },
};
