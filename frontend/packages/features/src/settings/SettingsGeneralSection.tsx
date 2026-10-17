import { Stack } from "@mantine/core";
import { BackendHealthSection } from "./BackendHealthSection.tsx";
import { BackendLogsSection } from "./BackendLogsSection.tsx";
import { CredentialsSection } from "./CredentialsSection.tsx";
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
  credentials: CredentialsState;
  onChangeCredentials: (patch: Partial<CredentialsState>) => void;
  onSubmitCredentials: () => void;
  log: LogState;
  onOpenLogs: () => void;
};

/**
 * General settings: health, upload dir, credentials, logs.
 */
export function SettingsGeneralSection({
  backendOnline,
  uploadDir,
  onOpenFolder,
  onBrowse,
  credentials,
  onChangeCredentials,
  onSubmitCredentials,
  log,
  onOpenLogs,
}: SettingsGeneralSectionProps) {
  return (
    <Stack gap="md">
      <BackendHealthSection backendOnline={backendOnline} />
      <UploadDirectorySection
        uploadDir={uploadDir}
        onOpenFolder={onOpenFolder}
        onBrowse={onBrowse}
      />
      <CredentialsSection
        credentials={credentials}
        onChange={onChangeCredentials}
        onSubmit={onSubmitCredentials}
      />
      <BackendLogsSection log={log} onOpenLogs={onOpenLogs} />
    </Stack>
  );
}
