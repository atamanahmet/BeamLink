import { useCallback, useEffect, useState } from "react";
import { problemFromUnknown } from "@beamlink/core";
import { usePlatform } from "@beamlink/platform";

export type AutostartControls = {
  capability: boolean;
  supported: boolean;
  enabled: boolean;
  ready: boolean;
  busy: boolean;
  error: string | null;
  correlationId: string | null;
  setEnabled: (enabled: boolean) => Promise<void>;
  refresh: () => Promise<void>;
};

/**
 * Reads and writes OS autostart. The OS is the source of truth.
 */
export function useAutostart(): AutostartControls {
  const platform = usePlatform();
  const capability = platform.capabilities.autostart;
  const [supported, setSupported] = useState(false);
  const [enabled, setEnabledState] = useState(false);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [correlationId, setCorrelationId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!capability) {
      void Promise.resolve().then(() => {
        if (!cancelled) {
          setSupported(false);
          setEnabledState(false);
          setReady(true);
        }
      });
      return () => {
        cancelled = true;
      };
    }
    void platform
      .getAutostart()
      .then((status) => {
        if (!cancelled) {
          setSupported(status.supported);
          setEnabledState(status.enabled);
          setError(null);
          setCorrelationId(null);
          setReady(true);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          const problem = problemFromUnknown(err);
          setSupported(false);
          setEnabledState(false);
          setError(problem.detail ?? "Autostart failed.");
          setCorrelationId(problem.correlationId);
          setReady(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [capability, platform]);

  /**
   * Reloads autostart status from the OS.
   */
  const refresh = useCallback(async () => {
    if (!capability) {
      setSupported(false);
      setEnabledState(false);
      setReady(true);
      return;
    }
    try {
      const status = await platform.getAutostart();
      setSupported(status.supported);
      setEnabledState(status.enabled);
      setError(null);
      setCorrelationId(null);
    } catch (err) {
      const problem = problemFromUnknown(err);
      setSupported(false);
      setEnabledState(false);
      setError(problem.detail ?? "Autostart failed.");
      setCorrelationId(problem.correlationId);
    } finally {
      setReady(true);
    }
  }, [capability, platform]);

  /**
   * Writes the OS autostart flag and refreshes local state.
   */
  const setEnabled = useCallback(
    async (next: boolean) => {
      if (!capability) {
        return;
      }
      setBusy(true);
      setError(null);
      setCorrelationId(null);
      try {
        const status = await platform.setAutostart(next);
        setSupported(status.supported);
        setEnabledState(status.enabled);
      } catch (err) {
        const problem = problemFromUnknown(err);
        setError(problem.detail ?? "Autostart failed.");
        setCorrelationId(problem.correlationId);
        throw err;
      } finally {
        setBusy(false);
      }
    },
    [capability, platform],
  );

  return {
    capability,
    supported: capability && supported,
    enabled,
    ready,
    busy,
    error,
    correlationId,
    setEnabled,
    refresh,
  };
}
