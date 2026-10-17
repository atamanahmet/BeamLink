import { useEffect, useState } from "react";
import type { SettingsApi } from "@beamlink/core";
import type { SpeedUnit } from "./speedUnits.ts";
import { fromMbps, parseSpeedInput } from "./speedUnits.ts";
import type { TransferStateFilter } from "./transferFilter.ts";

/**
 * Total limit draft and filter chip state for the transfers top bar.
 */
export function useTransferHeaderState(
  settingsApi: SettingsApi,
  enabled: boolean,
  unit: SpeedUnit,
) {
  const [filter, setFilter] = useState<TransferStateFilter>("ALL");
  const [totalLimitMbps, setTotalLimitMbps] = useState<number | null>(null);
  const [totalLimitDraft, setTotalLimitDraft] = useState<string | number>("");

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    void settingsApi
      .getTotalSpeedLimit()
      .then((data) => {
        if (cancelled) return;
        setTotalLimitMbps(data.value);
        setTotalLimitDraft(
          data.value == null ? "" : fromMbps(data.value, unit),
        );
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [enabled, settingsApi, unit]);

  /**
   * Saves the total speed limit; empty draft means unlimited.
   */
  const saveTotalLimit = async () => {
    const raw = totalLimitDraft === "" ? "" : String(totalLimitDraft);
    const parsed = parseSpeedInput(raw, unit);
    if (parsed !== null && Number.isNaN(parsed)) {
      return;
    }
    try {
      await settingsApi.putTotalSpeedLimit(parsed);
      setTotalLimitMbps(parsed);
    } catch {
      // Keep draft on failure.
    }
  };

  return {
    filter,
    setFilter,
    totalLimitMbps,
    totalLimitDraft,
    setTotalLimitDraft,
    saveTotalLimit,
  };
}
