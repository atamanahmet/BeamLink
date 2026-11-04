import { useMemo } from "react";
import { useNavigate } from "react-router";
import { useMantineColorScheme } from "@mantine/core";
import { SettingsView, useResumePolicySettings } from "@beamlink/features";
import { loadThemes, useThemeSettings } from "@beamlink/ui";
import { logout, logoutAll } from "../api/authApi";
import { RecoveryCodeSection } from "../components/RecoveryCodeSection.tsx";
import { SecurityLogSection } from "../components/SecurityLogSection.tsx";
import { healthApi } from "../api/transferApi";
import { settingsApi } from "../api/settingsApi";
import { useAuthStore } from "../store/authStore";

export function Component() {
  const navigate = useNavigate();
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
      onLogoutAll={() => void logoutAll()}
      themes={themes}
      themeId={themeId}
      onThemeIdChange={setThemeId}
      colorScheme={colorScheme}
      onColorSchemeChange={setColorScheme}
      resumePolicy={resumePolicy}
      generalExtra={<RecoveryCodeSection />}
      generalBottom={<SecurityLogSection />}
      dangerZoneKind="nexus"
      onAfterFactoryReset={async () => {
        useAuthStore.getState().clearSession();
        await window.electronAPI?.clearAuthToken?.();
        useAuthStore.getState().setStatus("setup");
        navigate("/setup", { replace: true });
      }}
    />
  );
}
