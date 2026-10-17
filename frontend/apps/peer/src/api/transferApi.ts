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
 * Peer transfer HTTP API.
 */
export const transferApi: TransferApi = {
  getAllTransfers: async () =>
    (await apiClient.get<TransferSummary[]>("/transfers")).data,

  getTransferLogs: async () =>
    (await apiClient.get<TransferLogEntry[]>("/logs")).data,

  getRecentTransferLogs: async (limit = 50) =>
    (await apiClient.get<TransferLogEntry[]>(`/logs/recent?limit=${limit}`))
      .data,

  getChildTransfers: async (id, type) => {
    if (type === "SINGLE") return [];
    return (
      await apiClient.get<TransferSummary[]>(
        `/transfers/${typePath(type)}/${id}/files`,
      )
    ).data;
  },

  initiateDispatch: async (req: InitiateDispatchRequest) =>
    (await apiClient.post<DispatchResultItem[]>("/transfers/send", req)).data,

  pauseTransfer: async (id, type) => {
    await apiClient.post(`/transfers/${typePath(type)}/${id}/pause`);
  },

  resumeTransfer: async (id, type) => {
    await apiClient.post(`/transfers/${typePath(type)}/${id}/resume`);
  },

  cancelTransfer: async (id, type) => {
    await apiClient.delete(`/transfers/${typePath(type)}/${id}`);
  },

  deleteTransfer: async (id, type) => {
    await apiClient.delete(`/transfers/${typePath(type)}/${id}/delete`);
  },

  setSpeedLimit: async (id, type, limitMbps) => {
    await apiClient.put(`/transfers/${typePath(type)}/${id}/speed-limit`, {
      limitMbps,
    });
  },

  openUploadsFolder: async () => {
    await apiClient.post("/storage/open-uploads-folder");
  },
};

/**
 * Peer peers HTTP API.
 */
export const peersApi: PeersApi = {
  getPeers: async (): Promise<PeerSummary[]> => {
    const res = await apiClient.get("/peers");
    const raw = res.data.peers ?? res.data;
    return (raw as Array<Record<string, unknown>>).map((p) => ({
      id: String(p.id),
      agentName: String(p.agentName ?? ""),
      ipAddress: String(p.ipAddress ?? ""),
      port: Number(p.port ?? 0),
      online: Boolean(p.online),
    }));
  },
};

/**
 * Peer health HTTP API.
 */
export const healthApi: HealthApi = {
  checkHealth: async () => {
    await healthClient.get("/actuator/health");
  },
};
