import { Stack, Tabs } from "@mantine/core";
import type { HealthApi, SettingsApi } from "@beamlink/core";
import { FileBrowserModal } from "../FileBrowserModal.tsx";
import { SettingsAppearanceSection } from "./SettingsAppearanceSection.tsx";
import { SettingsGeneralSection } from "./SettingsGeneralSection.tsx";
import { SettingsHeader } from "./SettingsHeader.tsx";
import { SettingsTransfersSection } from "./SettingsTransfersSection.tsx";
import { type ThemeSelectOption } from "./ThemeSelect.tsx";
import type { ResumePolicyControls } from "./useResumePolicySettings.ts";
import { useSettingsView } from "./useSettingsView.ts";

export type SettingsViewProps = {
  settingsApi: SettingsApi;
  healthApi: HealthApi;
  onLogout: () => void;
  themes: ThemeSelectOption[];
  themeId: string;
  onThemeIdChange: (id: string) => void;
  colorScheme: "light" | "dark" | "auto";
  onColorSchemeChange: (mode: "light" | "dark" | "auto") => void;
  resumePolicy?: ResumePolicyControls;
};

/**
 * Settings screen with General, Transfers, and Appearance tabs.
 */
export function SettingsView({
  settingsApi,
  healthApi,
  onLogout,
  themes,
  themeId,
  onThemeIdChange,
  colorScheme,
  onColorSchemeChange,
  resumePolicy,
}: SettingsViewProps) {
  const { tab, handleTabChange, backendOnline, ctrl, unit, setUnit } =
    useSettingsView({ settingsApi, healthApi, onLogout });

  return (
    <Stack gap="md" w="100%" maw={672} mx="auto">
      <SettingsHeader />

      <Tabs value={tab} onChange={handleTabChange}>
        <Tabs.List grow>
          <Tabs.Tab value="general">General</Tabs.Tab>
          <Tabs.Tab value="transfers">Transfers</Tabs.Tab>
          <Tabs.Tab value="appearance">Appearance</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="general" pt="md">
          <SettingsGeneralSection
            backendOnline={backendOnline}
            uploadDir={ctrl.uploadDir}
            onOpenFolder={() => void ctrl.openFolder()}
            onBrowse={() => ctrl.setFolderBrowserOpen(true)}
            credentials={ctrl.credentials}
            onChangeCredentials={(patch) =>
              ctrl.setCredentials((prev) => ({ ...prev, ...patch }))
            }
            onSubmitCredentials={() => void ctrl.changeCredentials()}
            log={ctrl.log}
            onOpenLogs={() => void ctrl.openLogs()}
          />
        </Tabs.Panel>

        <Tabs.Panel value="transfers" pt="md">
          <SettingsTransfersSection
            speedCap={ctrl.speedCap}
            onChangeSpeedCapValue={(value) =>
              ctrl.setSpeedCap((prev) => ({ ...prev, value, error: null }))
            }
            onSaveSpeedCap={() => void ctrl.saveSpeedCap()}
            unit={unit}
            onChangeUnit={setUnit}
            resumePolicy={resumePolicy}
          />
        </Tabs.Panel>

        <Tabs.Panel value="appearance" pt="md">
          <SettingsAppearanceSection
            themes={themes}
            themeId={themeId}
            onThemeIdChange={onThemeIdChange}
            colorScheme={colorScheme}
            onColorSchemeChange={onColorSchemeChange}
          />
        </Tabs.Panel>
      </Tabs>

      <FileBrowserModal
        isOpen={ctrl.folderBrowserOpen}
        onClose={() => ctrl.setFolderBrowserOpen(false)}
        mode="folder"
        title="Select Upload Directory"
        onConfirm={([path]) => {
          ctrl.setFolderBrowserOpen(false);
          void ctrl.saveUploadDir(path);
        }}
      />
    </Stack>
  );
}
