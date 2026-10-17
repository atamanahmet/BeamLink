import { useEffect, useRef } from "react";
import { apiClient } from "../api/client";
import { useAuthStore, type AuthUser } from "../store/authStore";
import type { AgentStatus } from "../api/agentApi";

type SseHandler = (data: unknown) => void;

/**
 * Peer SSE for identity and agent status. Not transfer progress.
 */
export function usePeerSse(onAgentStatus?: (data: AgentStatus) => void) {
  const status = useAuthStore((s) => s.status);
  const setUser = useAuthStore((s) => s.setUser);
  const handlersRef = useRef<Map<string, Set<SseHandler>>>(new Map());
  const onAgentStatusRef = useRef(onAgentStatus);

  useEffect(() => {
    onAgentStatusRef.current = onAgentStatus;
  });

  useEffect(() => {
    if (status !== "authenticated") return;

    const onSseEvent = (eventName: string, handler: SseHandler) => {
      if (!handlersRef.current.has(eventName)) {
        handlersRef.current.set(eventName, new Set());
      }
      handlersRef.current.get(eventName)!.add(handler);
      return () => void handlersRef.current.get(eventName)?.delete(handler);
    };

    const unsubIdentity = onSseEvent("identity_updated", (data) => {
      setUser(data as AuthUser);
    });

    const unsubStatus = onSseEvent("agent_status_updated", (data) => {
      onAgentStatusRef.current?.(data as AgentStatus);
    });

    const es = new EventSource(`${apiClient.defaults.baseURL}/events`);

    es.onmessage = (e) => {
      const { event, data } = JSON.parse(e.data);
      handlersRef.current.get(event)?.forEach((fn) => fn(data));
    };

    ["identity_updated", "agent_status_updated"].forEach((eventName) => {
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
  }, [status, setUser]);
}
