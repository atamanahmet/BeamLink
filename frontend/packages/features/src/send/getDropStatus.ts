export type DropStatus =
  | "sending"
  | "noPeer"
  | "offline"
  | "dragging"
  | "idle";

export type DropStatusInput = {
  isSending: boolean;
  hasPeer: boolean;
  isOffline: boolean;
  isDragging: boolean;
};

/** Precedence: sending, noPeer, offline, dragging, idle. */
export function getDropStatus({
  isSending,
  hasPeer,
  isOffline,
  isDragging,
}: DropStatusInput): DropStatus {
  if (isSending) return "sending";
  if (!hasPeer) return "noPeer";
  if (isOffline) return "offline";
  if (isDragging) return "dragging";
  return "idle";
}
