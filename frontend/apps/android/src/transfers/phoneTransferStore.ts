import type { TransferSummary } from "@beamlink/core";
import type { SendProgressEvent } from "../native/beamlinkPlugin.ts";
import { toTransferStatus } from "./phoneTransferMap.ts";

type SendMeta = {
  name: string;
  peer: string;
};

const jobs = new Map<string, TransferSummary>();
const listeners = new Set<() => void>();
let sendMeta: SendMeta = { name: "Send", peer: "Peer" };

/**
 * Notifies store subscribers.
 */
function emit(): void {
  for (const listener of listeners) {
    listener();
  }
}

/**
 * Empty TransferSummary used as a template for a live phone job.
 */
function blank(id: string): TransferSummary {
  return {
    id,
    dispatchId: null,
    type: "SINGLE",
    name: id === "receive" ? "Incoming file" : sendMeta.name,
    status: "ACTIVE",
    totalSize: 0,
    confirmedBytes: 0,
    totalFiles: 1,
    sourceDeviceId: null,
    targetDeviceId: id === "send" ? sendMeta.peer : "",
    targetIp: "",
    targetPort: 0,
    createdAt: new Date().toISOString(),
    completedAt: null,
    activeTransferMs: 0,
    failureReason: null,
    pausedBy: null,
    canResume: false,
    speedLimitMbps: null,
    effectiveLimitMbps: null,
  };
}

/**
 * Stores the send file name and peer shown on the live send card.
 */
export function rememberSendMeta(name: string, peer: string): void {
  sendMeta = {
    name: name.trim() || "Send",
    peer: peer.trim() || "Peer",
  };
}

/**
 * Upserts a send or receive row from a native progress event.
 */
export function applyProgress(event: SendProgressEvent): void {
  const id = event.direction === "receive" ? "receive" : "send";
  const prev = jobs.get(id) ?? blank(id);
  const status = toTransferStatus(event.state);
  jobs.set(id, {
    ...prev,
    name:
      id === "send"
        ? sendMeta.name
        : event.name?.trim() || prev.name,
    targetDeviceId: id === "send" ? sendMeta.peer : prev.targetDeviceId,
    sourceDeviceId:
      id === "receive"
        ? event.peer?.trim() || prev.sourceDeviceId
        : prev.sourceDeviceId,
    status,
    totalSize: event.total,
    confirmedBytes: event.bytes,
    failureReason: event.error ?? null,
    canResume: status === "PAUSED",
    completedAt:
      status === "COMPLETED" || status === "FAILED" || status === "CANCELLED"
        ? new Date().toISOString()
        : null,
  });
  emit();
}

/**
 * Snapshot for TransferApi.getAllTransfers.
 */
export function getPhoneTransfers(): TransferSummary[] {
  return [...jobs.values()];
}

/**
 * Subscribes to store changes. Returns unsubscribe.
 */
export function subscribePhoneTransfers(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
