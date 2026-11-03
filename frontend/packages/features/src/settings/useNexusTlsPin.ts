import { useCallback, useEffect, useRef, useState } from "react";
import { CONNECT_FAILED, problemFromUnknown } from "@beamlink/core";
import {
  draftFromStatus,
  parseNexusTlsUrl,
  shouldAutoProbe,
  type NexusTlsPinAdapter,
  type NexusTlsPinStatus,
} from "./nexusTlsPin.ts";

export type NexusTlsPinControls = {
  draft: string;
  setDraft: (value: string) => void;
  status: NexusTlsPinStatus;
  pendingFingerprint: string;
  error: string;
  correlationId: string | null;
  busy: boolean;
  probe: () => Promise<void>;
  confirm: () => Promise<void>;
  cancel: () => Promise<void>;
  rePin: () => Promise<void>;
};

const EMPTY: NexusTlsPinStatus = {
  url: "",
  pinSet: false,
  suggestedUrl: "",
  pendingFingerprint: "",
};

/**
 * First-contact probe and confirm for the nexus TLS pin.
 */
export function useNexusTlsPin(adapter: NexusTlsPinAdapter): NexusTlsPinControls {
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState<NexusTlsPinStatus>(EMPTY);
  const [pendingFingerprint, setPendingFingerprint] = useState("");
  const [error, setError] = useState("");
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const autoProbedRef = useRef("");

  const refresh = useCallback(async () => {
    const next = await adapter.getStatus();
    setStatus(next);
    setDraft((prev) => draftFromStatus(prev, next.url, next.suggestedUrl));
    if (next.pendingFingerprint) {
      setPendingFingerprint(next.pendingFingerprint);
    }
  }, [adapter]);

  useEffect(() => {
    let cancelled = false;
    void adapter
      .getStatus()
      .then((next) => {
        if (cancelled) return;
        setStatus(next);
        setDraft((prev) => draftFromStatus(prev, next.url, next.suggestedUrl));
        if (next.pendingFingerprint) {
          setPendingFingerprint(next.pendingFingerprint);
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [adapter]);

  useEffect(() => {
    if (status.pinSet) return;
    const id = window.setInterval(() => {
      void refresh();
    }, 2000);
    return () => window.clearInterval(id);
  }, [status.pinSet, refresh]);

  const probe = useCallback(async () => {
    setBusy(true);
    setError("");
    setCorrelationId(null);
    try {
      const { ip, port } = parseNexusTlsUrl(draft || status.suggestedUrl);
      const result = await adapter.probe(ip, port);
      setPendingFingerprint(result.fingerprint);
      setDraft(result.url);
      await refresh();
    } catch (err: unknown) {
      const problem = problemFromUnknown(err);
      setError(
        problem.detail ??
          (err instanceof Error ? err.message : CONNECT_FAILED),
      );
      setCorrelationId(problem.correlationId);
      autoProbedRef.current = "";
    } finally {
      setBusy(false);
    }
  }, [adapter, draft, status.suggestedUrl, refresh]);

  useEffect(() => {
    if (busy) return;
    if (!shouldAutoProbe(status.pinSet, status.suggestedUrl, pendingFingerprint)) {
      return;
    }
    if (autoProbedRef.current === status.suggestedUrl) return;
    autoProbedRef.current = status.suggestedUrl;
    void probe();
  }, [busy, status.pinSet, status.suggestedUrl, pendingFingerprint, probe]);

  const confirm = async () => {
    setBusy(true);
    setError("");
    setCorrelationId(null);
    try {
      const { ip, port } = parseNexusTlsUrl(draft);
      await adapter.confirm(ip, port, pendingFingerprint);
      setPendingFingerprint("");
      await refresh();
    } catch (err: unknown) {
      const problem = problemFromUnknown(err);
      setError(
        problem.detail ??
          (err instanceof Error ? err.message : "Nexus TLS confirm failed"),
      );
      setCorrelationId(problem.correlationId);
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    setPendingFingerprint("");
    setError("");
    setCorrelationId(null);
    await adapter.cancel();
  };

  const rePin = async () => {
    setBusy(true);
    setError("");
    setCorrelationId(null);
    try {
      await adapter.clearPin();
      setPendingFingerprint("");
      autoProbedRef.current = "";
      await refresh();
    } catch (err: unknown) {
      const problem = problemFromUnknown(err);
      setError(
        problem.detail ??
          (err instanceof Error ? err.message : "Clear pin failed"),
      );
      setCorrelationId(problem.correlationId);
    } finally {
      setBusy(false);
    }
  };

  return {
    draft,
    setDraft,
    status,
    pendingFingerprint,
    error,
    correlationId,
    busy,
    probe,
    confirm,
    cancel,
    rePin,
  };
}
