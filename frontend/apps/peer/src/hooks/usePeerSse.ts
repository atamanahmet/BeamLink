import { useEffect, useRef } from "react";
import { apiClient } from "../api/client";
import type { DeviceStatus } from "../api/deviceApi";

type SseHandler = (data: unknown) => void;

/**
 * Peer SSE for identity and device status. Not transfer progress.
 */
export function usePeerSse(onDeviceStatus?: (data: DeviceStatus) => void) {
  const handlersRef = useRef<Map<string, Set<SseHandler>>>(new Map());
  const onDeviceStatusRef = useRef(onDeviceStatus);

  useEffect(() => {
    onDeviceStatusRef.current = onDeviceStatus;
  });

  useEffect(() => {
    const onSseEvent = (eventName: string, handler: SseHandler) => {
      if (!handlersRef.current.has(eventName)) {
        handlersRef.current.set(eventName, new Set());
      }
      handlersRef.current.get(eventName)!.add(handler);
      return () => void handlersRef.current.get(eventName)?.delete(handler);
    };

    const unsubStatus = onSseEvent("device_status_updated", (data) => {
      onDeviceStatusRef.current?.(data as DeviceStatus);
    });

    const unsubIdentity = onSseEvent("identity_updated", () => {
      // Status refresh is handled by the parent after identity changes.
    });

    const es = new EventSource(`${apiClient.defaults.baseURL}/events`);

    es.onmessage = (e) => {
      const { event, data } = JSON.parse(e.data);
      handlersRef.current.get(event)?.forEach((fn) => fn(data));
    };

    ["identity_updated", "device_status_updated"].forEach((eventName) => {
      es.addEventListener(eventName, (e) => {
        const data = JSON.parse((e as MessageEvent).data);
        handlersRef.current.get(eventName)?.forEach((fn) => fn(data));
      });
    });

    es.onerror = () => es.close();

    return () => {
      unsubIdentity();
      unsubStatus();
      es.close();
    };
  }, []);
}
