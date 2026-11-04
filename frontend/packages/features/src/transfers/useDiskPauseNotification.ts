import { useEffect, useRef } from "react";
import type { TransferSummary } from "@beamlink/core";
import {
  diskPauseNotificationCopy,
  isReceiveDiskPause,
} from "./diskPauseNotification.ts";

/**
 * Fires one web Notification per receive disk pause while the window is unfocused.
 */
export function useDiskPauseNotification(
  transfers: TransferSummary[],
  localDeviceId: string | null,
) {
  const shown = useRef(new Set<string>());
  const notes = useRef(new Map<string, Notification>());

  useEffect(() => {
    if (typeof Notification === "undefined") {
      return;
    }
    const live = new Set<string>();
    for (const t of transfers) {
      if (!isReceiveDiskPause(t, localDeviceId)) {
        continue;
      }
      live.add(t.id);
      if (shown.current.has(t.id)) {
        continue;
      }
      shown.current.add(t.id);
      if (document.hasFocus()) {
        continue;
      }
      void showNote(t.id, notes.current);
    }
    for (const id of [...shown.current]) {
      if (live.has(id)) {
        continue;
      }
      shown.current.delete(id);
      notes.current.get(id)?.close();
      notes.current.delete(id);
    }
  }, [transfers, localDeviceId]);
}

/**
 * Requests permission once, then shows a tagged Notification.
 */
async function showNote(id: string, notes: Map<string, Notification>) {
  if (Notification.permission === "default") {
    await Notification.requestPermission();
  }
  if (Notification.permission !== "granted") {
    return;
  }
  const copy = diskPauseNotificationCopy();
  const note = new Notification(copy.title, {
    body: copy.body,
    tag: id,
  });
  note.onclick = () => {
    const api = (
      window as unknown as {
        electronAPI?: { showWindow?: () => Promise<unknown> };
      }
    ).electronAPI;
    void api?.showWindow?.();
    window.focus();
    window.location.hash = "#/transfers";
    note.close();
  };
  notes.set(id, note);
}
