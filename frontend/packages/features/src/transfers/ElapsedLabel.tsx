import { useEffect, useState } from "react";
import { isStalledPending } from "./transferPoll.ts";

/**
 * Formats an elapsed duration for display.
 */
function formatElapsedMs(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

type ElapsedLabelProps = {
  createdAt: string;
  /** When true, show stalled after the shared PENDING stall age. */
  pending?: boolean;
};

/**
 * Own 1s tick so the parent list does not re-render.
 */
export function ElapsedLabel({ createdAt, pending = false }: ElapsedLabelProps) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  if (pending && isStalledPending("PENDING", createdAt, now)) {
    return <>stalled</>;
  }

  const ms = now - new Date(createdAt).getTime();
  return <>{formatElapsedMs(Math.max(0, ms))}</>;
}
