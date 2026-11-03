import type { TransferSummary, TransferType } from "./transfer.ts";
import type { PeerSummary } from "./peer.ts";

export type DispatchResultItem = {
  id: string;
  type: TransferType;
};

export type InitiateDispatchResponse = {
  items: DispatchResultItem[];
  skippedCount: number;
};

export type InitiateDispatchRequest = {
  paths: string[];
  targetDeviceId: string;
  targetIp: string;
  targetPort: number;
};

export type TransferLogEntry = {
  id: string;
  fromDeviceId: string;
  fromDeviceName: string;
  toDeviceId: string;
  toDeviceName: string;
  filename: string;
  fileSize: number;
  timestamp: string;
};

export type TransferListPage = {
  items: TransferSummary[];
  total: number;
  hasMore: boolean;
};

/**
 * Transfer list and actions used by both apps.
 */
export interface TransferApi {
  getAllTransfers: () => Promise<TransferSummary[]>;
  /** Optional paged list for nexus admin. */
  getTransfersPage?: (
    page: number,
    size?: number,
  ) => Promise<TransferListPage>;
  getTransferLogs: () => Promise<TransferLogEntry[]>;
  getRecentTransferLogs: (limit?: number) => Promise<TransferLogEntry[]>;
  getChildTransfers: (
    id: string,
    type: TransferType,
  ) => Promise<TransferSummary[]>;
  initiateDispatch: (
    req: InitiateDispatchRequest,
  ) => Promise<InitiateDispatchResponse>;
  pauseTransfer: (id: string, type: TransferType) => Promise<void>;
  resumeTransfer: (id: string, type: TransferType) => Promise<void>;
  cancelTransfer: (id: string, type: TransferType) => Promise<void>;
  deleteTransfer: (id: string, type: TransferType) => Promise<void>;
  setSpeedLimit: (
    id: string,
    type: TransferType,
    limitMbps: number | null,
  ) => Promise<void>;
  openUploadsFolder: () => Promise<void>;
}

/**
 * Peer list for send and transfer screens.
 */
export interface PeersApi {
  getPeers: () => Promise<PeerSummary[]>;
}

/**
 * Backend health check used by Settings.
 */
export interface HealthApi {
  checkHealth: () => Promise<void>;
}

export type UploadDirResponse = {
  path: string;
  error?: string;
};

export type SpeedCapResponse = {
  value: number | null;
};

export type ResumePolicyMode = "OWNER_ONLY" | "ANYONE";

export type ResumePolicyResponse = {
  resumePolicy: ResumePolicyMode;
};

export type ChangeCredentialsRequest = {
  currentPassword: string;
  newUsername: string;
  newPassword: string;
};

export type DeviceReRegisterResult = {
  nexusRemoved: boolean;
  state: string;
  message: string;
};

/**
 * Settings screen storage, speed cap, credentials and logs.
 */
export interface SettingsApi {
  getUploadDir: () => Promise<UploadDirResponse>;
  putUploadDir: (path: string) => Promise<UploadDirResponse>;
  getSpeedCap: () => Promise<SpeedCapResponse>;
  putSpeedCap: (value: number) => Promise<void>;
  getTotalSpeedLimit: () => Promise<SpeedCapResponse>;
  putTotalSpeedLimit: (value: number | null) => Promise<void>;
  getResumePolicy: () => Promise<ResumePolicyResponse>;
  putResumePolicy: (value: ResumePolicyMode) => Promise<void>;
  getMinFreeSpace: () => Promise<{ value: number }>;
  putMinFreeSpace: (value: number) => Promise<void>;
  openUploadsFolder: () => Promise<void>;
  openLogs: () => Promise<void>;
  changeCredentials: (req: ChangeCredentialsRequest) => Promise<void>;
  reRegister?: (force?: boolean) => Promise<DeviceReRegisterResult>;
  resetNetworkData?: (force?: boolean) => Promise<void>;
  factoryReset?: (force?: boolean) => Promise<void>;
}
