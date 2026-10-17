import { apiClient } from "./client";
import type { TransferLogEntry } from "@beamlink/core";

export type NexusAgent = {
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
};

export type NexusStats = {
  agentStats: {
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
  getAgents: () => Promise<NexusAgent[]>;
  getApprovedAgents: () => Promise<NexusAgent[]>;
  getPendingAgents: () => Promise<NexusAgent[]>;
  getPendingRenames: () => Promise<NexusAgent[]>;
  getTransferLogs: () => Promise<TransferLogEntry[]>;
  approveAgent: (id: string) => Promise<void>;
  rejectAgent: (id: string) => Promise<void>;
  removeAgent: (id: string) => Promise<void>;
  approveRename: (id: string) => Promise<void>;
  rejectRename: (id: string) => Promise<void>;
}

/**
 * Nexus admin HTTP service.
 */
export const nexusAdminApi: NexusAdminApi = {
  getStats: async () =>
    (await apiClient.get<NexusStats>("/admin/dashboard/stats")).data,

  getAgents: async () =>
    (await apiClient.get<NexusAgent[]>("/admin/agents")).data,

  getApprovedAgents: async () =>
    (await apiClient.get<NexusAgent[]>("/admin/agents/approved")).data,

  getPendingAgents: async () =>
    (await apiClient.get<NexusAgent[]>("/admin/agents/pending")).data,

  getPendingRenames: async () =>
    (await apiClient.get<NexusAgent[]>("/admin/agents/rename-pending")).data,

  getTransferLogs: async () =>
    (await apiClient.get<TransferLogEntry[]>("/admin/logs")).data,

  approveAgent: async (id) => {
    await apiClient.post(`/admin/agents/${id}/approve`);
  },

  rejectAgent: async (id) => {
    await apiClient.post(`/admin/agents/${id}/reject`);
  },

  removeAgent: async (id) => {
    await apiClient.delete(`/admin/agents/${id}`);
  },

  approveRename: async (id) => {
    await apiClient.post(`/admin/agents/${id}/rename/approve`);
  },

  rejectRename: async (id) => {
    await apiClient.post(`/admin/agents/${id}/rename/reject`);
  },
};
