import { registerPlugin } from "@capacitor/core";
import type { PeerSummary, SharedFileItem } from "@beamlink/core";

export type RegistrationStatus = {
  state: string;
  message: string;
  hasTokens: boolean;
  deviceId?: string;
  fingerprint?: string;
};

export type SendProgressEvent = {
  bytes: number;
  total: number;
  state: string;
  error?: string;
  direction?: "send" | "receive";
  name?: string;
  peer?: string;
};

export type ReceiveToggleEvent = {
  enabled: boolean;
  message: string;
};

type BeamlinkNative = {
  getNexusAddress(): Promise<{ url: string }>;
  setNexusAddress(options: { url: string }): Promise<void>;
  probeNexus(options: { url: string }): Promise<{ fingerprint: string; url: string }>;
  confirmNexusPin(options: { url: string; fingerprint: string }): Promise<void>;
  clearNexusPin(): Promise<void>;
  getNexusTlsStatus(): Promise<{
    url: string;
    pinSet: boolean;
    pendingFingerprint: string;
  }>;
  getRegistrationStatus(): Promise<RegistrationStatus>;
  register(): Promise<void>;
  reRegister(): Promise<void>;
  resetDevice(): Promise<void>;
  startIdentifyPoll(): Promise<void>;
  stopIdentifyPoll(): Promise<void>;
  getPeers(): Promise<{ peers: PeerSummary[] }>;
  pickFiles(): Promise<{ files: SharedFileItem[] }>;
  sendFiles(options: {
    files: SharedFileItem[];
    targetDeviceId: string;
    targetIp: string;
    targetPort: number;
    tlsPort?: number | null;
    tlsFingerprint?: string | null;
  }): Promise<void>;
  cancelSend(): Promise<void>;
  resumeSend(): Promise<void>;
  getReceiveEnabled(): Promise<{ enabled: boolean }>;
  getTransferSlots(): Promise<{ slots: SendProgressEvent[] }>;
  setReceiveEnabled(options: { enabled: boolean }): Promise<{ enabled: boolean }>;
  getMinFreeSpaceBytes(): Promise<{ value: number }>;
  setMinFreeSpaceBytes(options: { value: number }): Promise<{ value: number }>;
  cancelReceive(): Promise<void>;
  resumeReceive(): Promise<void>;
  setStatusBarLight(options: { light: boolean }): Promise<void>;
  addListener(
    eventName: "registrationState",
    listenerFunc: (event: RegistrationStatus) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    eventName: "sendProgress",
    listenerFunc: (event: SendProgressEvent) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    eventName: "receiveToggle",
    listenerFunc: (event: ReceiveToggleEvent) => void,
  ): Promise<{ remove: () => Promise<void> }>;
  addListener(
    eventName: "openTransfers",
    listenerFunc: () => void,
  ): Promise<{ remove: () => Promise<void> }>;
};

export const Beamlink = registerPlugin<BeamlinkNative>("Beamlink");
