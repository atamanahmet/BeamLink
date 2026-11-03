export type PeerSummary = {
  id: string;
  deviceName: string;
  ipAddress: string;
  port: number;
  online: boolean;
  tlsPort?: number | null;
  tlsFingerprint?: string | null;
};
