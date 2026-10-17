import type {
  DispatchResultItem,
  HealthApi,
  InitiateDispatchRequest,
  PeerSummary,
  PeersApi,
  TransferApi,
  TransferLogEntry,
  TransferSummary,
  TransferType,
} from "@beamlink/core";
import { apiClient, healthClient } from "./client";

const typePath = (type: TransferType) => type.toLowerCase();

/**
 * Nexus transfer HTTP API under /admin.
 */
export const transferApi: TransferApi = {
  getAllTransfers: async () =>
    (await apiClient.get<TransferSummary[]>("/admin/transfers")).data,

  getTransferLogs: async () =>
    (await apiClient.get<TransferLogEntry[]>("/admin/logs")).data,

  getRecentTransferLogs: async (limit = 50) =>
    (
      await apiClient.get<TransferLogEntry[]>(
        `/admin/logs/recent?limit=${limit}`,
      )
    ).data,

  getChildTransfers: async (id, type) => {
    if (type === "SINGLE") return [];
    return (
      await apiClient.get<TransferSummary[]>(
        `/admin/transfers/${typePath(type)}/${id}/files`,
      )
    ).data;
  },

  initiateDispatch: async (req: InitiateDispatchRequest) =>
    (await apiClient.post<DispatchResultItem[]>("/admin/transfers/send", req))
      .data,

  pauseTransfer: async (id, type) => {
    await apiClient.post(`/admin/transfers/${typePath(type)}/${id}/pause`);
  },

  resumeTransfer: async (id, type) => {
    await apiClient.post(`/admin/transfers/${typePath(type)}/${id}/resume`);
  },

  cancelTransfer: async (id, type) => {
    await apiClient.delete(`/admin/transfers/${typePath(type)}/${id}`);
  },

  deleteTransfer: async (id, type) => {
    await apiClient.delete(`/admin/transfers/${typePath(type)}/${id}/delete`);
  },

  setSpeedLimit: async (id, type, limitMbps) => {
    await apiClient.put(
      `/admin/transfers/${typePath(type)}/${id}/speed-limit`,
      { limitMbps },
    );
  },

  openUploadsFolder: async () => {
    await apiClient.post("/storage/open-uploads-folder");
  },
};

/**
 * Nexus peers HTTP API.
 */
export const peersApi: PeersApi = {
  getPeers: async (): Promise<PeerSummary[]> => {
    const res = await apiClient.get("/peers");
    const raw = res.data.peers ?? res.data;
    return (raw as Array<Record<string, unknown>>).map((p) => ({
      id: String(p.id ?? p.agentId),
      agentName: String(p.agentName ?? ""),
      ipAddress: String(p.ipAddress ?? ""),
      port: Number(p.port ?? 0),
      online: Boolean(p.online),
    }));
  },
};

/**
 * Nexus health HTTP API.
 */
export const healthApi: HealthApi = {
  checkHealth: async () => {
    await healthClient.get("/actuator/health");
  },
};
