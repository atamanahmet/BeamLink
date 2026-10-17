import { useState } from "react";
import type { HealthApi, SettingsApi } from "@beamlink/core";
import { useHealthQuery } from "../hooks/useHealthQuery.ts";
import { useUnitPreference } from "../transfers/useUnitPreference.ts";
import { useSettingsController } from "./useSettingsController.ts";

type SettingsTab = "general" | "transfers" | "appearance";

type UseSettingsViewArgs = {
  settingsApi: SettingsApi;
  healthApi: HealthApi;
  onLogout: () => void;
};

/**
 * Settings tab, health, unit and controller data for the view.
 */
export function useSettingsView({
  settingsApi,
  healthApi,
  onLogout,
}: UseSettingsViewArgs) {
  const [tab, setTab] = useState<SettingsTab>("general");
  const healthQuery = useHealthQuery(healthApi);
  const backendOnline = healthQuery.isError
    ? false
    : healthQuery.isSuccess
      ? true
      : null;

  const ctrl = useSettingsController({
    settingsApi,
    onLogout,
  });
  const { unit, setUnit } = useUnitPreference();

  /**
   * Keeps tab state on known values only.
   */
  const handleTabChange = (value: string | null) => {
    if (value === "general" || value === "transfers" || value === "appearance") {
      setTab(value);
    }
  };

  return {
    tab,
    handleTabChange,
    backendOnline,
    ctrl,
    unit,
    setUnit,
  };
}
