export type { PauseOwner, TransferSummary, TransferType } from "./transfer.ts";
export type { PeerSummary } from "./peer.ts";
export type {
  DispatchResultItem,
  InitiateDispatchRequest,
  TransferLogEntry,
  TransferApi,
  PeersApi,
  HealthApi,
  UploadDirResponse,
  SpeedCapResponse,
  ResumePolicyMode,
  ResumePolicyResponse,
  ChangeCredentialsRequest,
  SettingsApi,
} from "./api.ts";

export type Platform = {
  readonly env: {
    readonly sep: string;
    readonly homedir: string;
  };
  getPathForFiles: (files: File[]) => Promise<string[]>;
  getRoots: () => Promise<
    {
      name: string;
      fullPath: string;
      isDirectory: true;
      locked: boolean;
      hidden: boolean;
    }[]
  >;
  readDir: (dirPath: string) => Promise<
    {
      name: string;
      fullPath: string;
      isDirectory: boolean;
      size?: number;
      locked: boolean;
      hidden: boolean;
    }[]
  >;
  mkdir: (dirPath: string) => Promise<void>;
  exists: (dirPath: string) => Promise<boolean>;
  onBackendStatus: (
    cb: (data: {
      status: "starting" | "ready" | "error";
      backendUrl?: string;
      message?: string;
      attempt?: number;
      max?: number;
    }) => void,
  ) => Promise<void>;
  getBackendStatus: () => Promise<{
    status: "starting" | "ready" | "error";
    backendUrl?: string;
    message?: string;
  }>;
  setWindowTheme?: (colors: {
    background: string;
    foreground: string;
  }) => Promise<void>;
};
