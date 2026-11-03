export type TransferType = "SINGLE" | "BATCH" | "DIRECTORY";

export type PauseOwner = "SOURCE" | "TARGET";

export type TransferSummary = {
  id: string;
  dispatchId: string | null;
  type: TransferType;
  name: string;
  status: string;
  totalSize: number;
  confirmedBytes: number;
  totalFiles: number;
  sourceDeviceId: string | null;
  targetDeviceId: string;
  targetIp: string;
  targetPort: number;
  createdAt: string;
  completedAt: string | null;
  activeTransferMs: number;
  failureReason: string | null;
  pausedBy: PauseOwner | null;
  canResume: boolean;
  speedLimitMbps: number | null;
  effectiveLimitMbps: number | null;
};
