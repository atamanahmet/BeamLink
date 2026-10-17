import { useMemo } from "react";
import { useMantineColorScheme } from "@mantine/core";
import { SettingsView, useResumePolicySettings } from "@beamlink/features";
import { loadThemes, useThemeSettings } from "@beamlink/ui";
import { logout } from "../api/authApi";
import { healthApi } from "../api/transferApi";
import { settingsApi } from "../api/settingsApi";

export function Component() {
  const { themeId, setThemeId } = useThemeSettings();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const themes = useMemo(
    () => loadThemes().map((theme) => ({ id: theme.id, name: theme.name })),
    [],
  );
  const resumePolicy = useResumePolicySettings(settingsApi);

  return (
    <SettingsView
      settingsApi={settingsApi}
      healthApi={healthApi}
      onLogout={() => void logout()}
      themes={themes}
      themeId={themeId}
      onThemeIdChange={setThemeId}
      colorScheme={colorScheme}
      onColorSchemeChange={setColorScheme}
      resumePolicy={resumePolicy}
    />
  );
}
