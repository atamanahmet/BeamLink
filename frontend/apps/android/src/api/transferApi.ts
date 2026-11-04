import type {
  DispatchResultItem,
  HealthApi,
  PeerSummary,
  PeersApi,
  TransferApi,
  TransferLogEntry,
  TransferSummary,
} from "@beamlink/core";
import { Beamlink } from "../native/beamlinkPlugin.ts";
import { getPhoneTransfers } from "../transfers/phoneTransferStore.ts";

/**
 * Peer transfer API. Send goes through the native plugin, not a local device.
 */
export const transferApi: TransferApi = {
  getAllTransfers: async () => getPhoneTransfers(),

  getTransferLogs: async () => [] as TransferLogEntry[],

  getRecentTransferLogs: async () => [] as TransferLogEntry[],

  getChildTransfers: async () => [] as TransferSummary[],

  initiateDispatch: async () => {
    throw new Error("Use native sendFiles with SharedFileItem refs on Android");
  },

  pauseTransfer: async () => {
    throw new Error("Pause is not supported in this batch");
  },

  resumeTransfer: async (id: string) => {
    if (id === "receive") {
      await Beamlink.resumeReceive();
      return;
    }
    if (id === "send") {
      await Beamlink.resumeSend();
    }
  },

  cancelTransfer: async (id: string) => {
    if (id === "receive") {
      await Beamlink.cancelReceive();
      return;
    }
    await Beamlink.cancelSend();
  },

  deleteTransfer: async () => {},

  setSpeedLimit: async () => {},

  openUploadsFolder: async () => {},
};

/**
 * Peers from nexus summary via the native adapter.
 */
export const peersApi: PeersApi = {
  getPeers: async (): Promise<PeerSummary[]> => {
    const result = await Beamlink.getPeers();
    return result.peers ?? [];
  },
};

/**
 * Health is unused on Android lite; settings show nexus registration instead.
 */
export const healthApi: HealthApi = {
  checkHealth: async () => {},
};

export type { DispatchResultItem };
