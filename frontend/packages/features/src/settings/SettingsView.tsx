import { useCallback, type ReactNode } from "react";
import { Button, Stack, Tabs } from "@mantine/core";
import { CONNECT_LABEL, type HealthApi, type SettingsApi } from "@beamlink/core";
import { FileBrowserModal } from "../FileBrowserModal.tsx";
import { LanAddressesSection } from "./LanAddressesSection.tsx";
import { SettingsAppearanceSection } from "./SettingsAppearanceSection.tsx";
import { SettingsGeneralSection } from "./SettingsGeneralSection.tsx";
import { SettingsHeader } from "./SettingsHeader.tsx";
import { SettingsTransfersSection } from "./SettingsTransfersSection.tsx";
import { type ThemeSelectOption } from "./ThemeSelect.tsx";
import type { ResumePolicyControls } from "./useResumePolicySettings.ts";
import type { NexusTlsPinAdapter } from "./nexusTlsPin.ts";
import { useConnectOpen } from "./useConnectFlow.ts";
import { useSettingsView } from "./useSettingsView.ts";

export type SettingsViewProps = {
  settingsApi: SettingsApi;
  healthApi: HealthApi;
  onLogout: () => void;
  /** When false, hides the credentials form (peer app). Default true. */
  showCredentials?: boolean;
  /** Revokes all admin sessions when set (nexus only). */
  onLogoutAll?: () => void;
  themes: ThemeSelectOption[];
  themeId: string;
  onThemeIdChange: (id: string) => void;
  colorScheme: "light" | "dark" | "auto";
  onColorSchemeChange: (mode: "light" | "dark" | "auto") => void;
  resumePolicy?: ResumePolicyControls;
  dangerZoneKind?: "device" | "nexus";
  onAfterFactoryReset?: () => void | Promise<void>;
  nexusTlsPin?: NexusTlsPinAdapter;
  generalExtra?: ReactNode;
  generalBottom?: ReactNode;
};

/**
 * Settings screen with General, Transfers, and Appearance tabs.
 */
export function SettingsView({
  settingsApi,
  healthApi,
  onLogout,
  showCredentials = true,
  onLogoutAll,
  themes,
  themeId,
  onThemeIdChange,
  colorScheme,
  onColorSchemeChange,
  resumePolicy,
  dangerZoneKind,
  onAfterFactoryReset,
  nexusTlsPin,
  generalExtra,
  generalBottom,
}: SettingsViewProps) {
  const { tab, handleTabChange, backendOnline, ctrl, unit, setUnit } =
    useSettingsView({ settingsApi, healthApi, onLogout });
  const openConnect = useConnectOpen();
  const getLanAddresses = settingsApi.getLanAddresses;
  const loadLanAddresses = useCallback(
    () => (getLanAddresses ? getLanAddresses() : Promise.reject()),
    [getLanAddresses],
  );

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
          <Stack gap="md">
            {nexusTlsPin ? (
              <Button onClick={openConnect}>
                {CONNECT_LABEL}
              </Button>
            ) : null}
            {getLanAddresses ? (
              <LanAddressesSection load={loadLanAddresses} />
            ) : null}
            <SettingsGeneralSection
              backendOnline={backendOnline}
              uploadDir={ctrl.uploadDir}
              onOpenFolder={() => void ctrl.openFolder()}
              onBrowse={() => ctrl.setFolderBrowserOpen(true)}
              showCredentials={showCredentials}
              credentials={ctrl.credentials}
              onChangeCredentials={(patch) =>
                ctrl.setCredentials((prev) => ({ ...prev, ...patch }))
              }
              onSubmitCredentials={() => void ctrl.changeCredentials()}
              onLogoutAll={onLogoutAll}
              log={ctrl.log}
              onOpenLogs={() => void ctrl.openLogs()}
              settingsApi={settingsApi}
              dangerZoneKind={dangerZoneKind}
              onAfterFactoryReset={onAfterFactoryReset}
              generalExtra={generalExtra}
              generalBottom={generalBottom}
            />
          </Stack>
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
            minFreeSpace={ctrl.minFreeSpace}
            onChangeMinFreeSpaceValue={(value) =>
              ctrl.setMinFreeSpace((prev) => ({
                ...prev,
                value,
                error: null,
              }))
            }
            onSaveMinFreeSpace={() => void ctrl.saveMinFreeSpace()}
            onResetMinFreeSpace={() => void ctrl.resetMinFreeSpace()}
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
