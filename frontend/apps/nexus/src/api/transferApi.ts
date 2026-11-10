import type {
  HealthApi,
  InitiateDispatchRequest,
  InitiateDispatchResponse,
  PeerSummary,
  PeersApi,
  TransferApi,
  TransferListPage,
  TransferLogEntry,
  TransferSummary,
  TransferType,
} from "@beamlink/core";
import { apiClient, healthClient } from "./client";

const typePath = (type: TransferType) => type.toLowerCase();

/**
 * One page of admin transfers.
 */
async function fetchTransfersPage(
  page: number,
  size = 50,
): Promise<TransferListPage> {
  return (
    await apiClient.get<TransferListPage>("/admin/transfers", {
      params: { page, size },
    })
  ).data;
}

/**
 * Nexus transfer HTTP API under /admin.
 */
export const transferApi: TransferApi = {
  getTransfersPage: fetchTransfersPage,

  getAllTransfers: async () => (await fetchTransfersPage(0, 50)).items,

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
    (await apiClient.post<InitiateDispatchResponse>(
      "/admin/transfers/send",
      req,
    )).data,

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
    const res = await apiClient.get("/peers/summary");
    const raw = res.data.peers ?? res.data;
    return (raw as Array<Record<string, unknown>>).map((p) => ({
      id: String(p.id),
      deviceName: String(p.deviceName ?? ""),
      ipAddress: String(p.ipAddress ?? ""),
      port: Number(p.port ?? 0),
      online: Boolean(p.online),
      tlsPort:
        p.tlsPort == null || p.tlsPort === ""
          ? null
          : Number(p.tlsPort),
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
