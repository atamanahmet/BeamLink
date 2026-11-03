import type { ReactNode } from "react";
import { Stack } from "@mantine/core";
import { usePlatform } from "@beamlink/platform";
import { BackendHealthSection } from "./BackendHealthSection.tsx";
import type { SettingsApi } from "@beamlink/core";
import { BackendLogsSection } from "./BackendLogsSection.tsx";
import { CredentialsSection } from "./CredentialsSection.tsx";
import { DangerZoneSection } from "./DangerZoneSection.tsx";
import { LogoutEverywhereSection } from "./LogoutEverywhereSection.tsx";
import { UploadDirectorySection } from "./UploadDirectorySection.tsx";
import type {
  CredentialsState,
  LogState,
  UploadDirState,
} from "./types.ts";

export type SettingsGeneralSectionProps = {
  backendOnline: boolean | null;
  uploadDir: UploadDirState;
  onOpenFolder: () => void;
  onBrowse: () => void;
  showCredentials?: boolean;
  credentials: CredentialsState;
  onChangeCredentials: (patch: Partial<CredentialsState>) => void;
  onSubmitCredentials: () => void;
  onLogoutAll?: () => void;
  log: LogState;
  onOpenLogs: () => void;
  settingsApi?: SettingsApi;
  dangerZoneKind?: "device" | "nexus";
  onAfterFactoryReset?: () => void | Promise<void>;
  generalExtra?: ReactNode;
  generalBottom?: ReactNode;
};

/**
 * General settings: health, upload dir, credentials, logs.
 */
export function SettingsGeneralSection({
  backendOnline,
  uploadDir,
  onOpenFolder,
  onBrowse,
  showCredentials = true,
  credentials,
  onChangeCredentials,
  onSubmitCredentials,
  onLogoutAll,
  log,
  onOpenLogs,
  settingsApi,
  dangerZoneKind,
  onAfterFactoryReset,
  generalExtra,
  generalBottom,
}: SettingsGeneralSectionProps) {
  const { capabilities } = usePlatform();

  return (
    <Stack gap="md">
      <BackendHealthSection backendOnline={backendOnline} />
      <UploadDirectorySection
        uploadDir={uploadDir}
        onOpenFolder={onOpenFolder}
        onBrowse={onBrowse}
      />
      {showCredentials ? (
        <CredentialsSection
          credentials={credentials}
          onChange={onChangeCredentials}
          onSubmit={onSubmitCredentials}
        />
      ) : null}
      {onLogoutAll ? (
        <LogoutEverywhereSection onLogoutAll={onLogoutAll} />
      ) : null}
      {generalExtra}
      {capabilities.transferLogs ? (
        <BackendLogsSection log={log} onOpenLogs={onOpenLogs} />
      ) : null}
      {settingsApi && dangerZoneKind ? (
        <DangerZoneSection
          settingsApi={settingsApi}
          kind={dangerZoneKind}
          onAfterFactoryReset={onAfterFactoryReset}
        />
      ) : null}
      {generalBottom}
    </Stack>
  );
}
