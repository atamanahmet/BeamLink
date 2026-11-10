export type { PauseOwner, TransferSummary, TransferType } from "./transfer.ts";
export type { Problem } from "./problem.ts";
export {
  parseProblem,
  mapErrorReason,
  problemFromUnknown,
} from "./problem.ts";
export { parseRetryAfter } from "./parseRetryAfter.ts";
export { GIB_IN_BYTES, gibToBytes, bytesToGib } from "./bytes.ts";
export { BIG_DROP_FILE_LIMIT, bigDropRule } from "./bigDropRule.ts";
export type { BigDropInput } from "./bigDropRule.ts";
export {
  CONNECT_LABEL,
  LATER_LABEL,
  BACK_LABEL,
  CONFIRM_LABEL,
  FIND_NEXUS_TITLE,
  VERIFY_FINGERPRINT_TITLE,
  WAITING_APPROVAL_TITLE,
  DEVICE_KEY_LABEL,
  NEXUS_CERTIFICATE_LABEL,
  FINGERPRINT_MATCH_TEXT,
  WAITING_APPROVAL_BODY,
  BANNER_DISCONNECTED,
  BANNER_CONNECT,
  BANNER_PENDING,
  BANNER_SHOW,
  BANNER_CONNECT_AGAIN,
  PHONE_START_LINE,
  CONNECT_FAILED,
  DEVICE_STATE_UNREGISTERED,
  DEVICE_STATE_PENDING_APPROVAL,
  DEVICE_STATE_APPROVED,
  DEVICE_STATE_REJECTED,
  PHONE_STATE_NOT_CONFIGURED,
  PHONE_STATE_REGISTERING,
  PHONE_STATE_PENDING,
  PHONE_STATE_APPROVED,
  PHONE_STATE_REJECTED,
  PHONE_STATE_ERROR,
  connectNeedsAction,
  connectWaiting,
  connectBanner,
  type ConnectBanner,
} from "./connectCopy.ts";

export type { PeerSummary } from "./peer.ts";
export type {
  DispatchResultItem,
  InitiateDispatchResponse,
  InitiateDispatchRequest,
  TransferLogEntry,
  TransferListPage,
  TransferApi,
  PeersApi,
  HealthApi,
  UploadDirResponse,
  SpeedCapResponse,
  ResumePolicyMode,
  ResumePolicyResponse,
  ChangeCredentialsRequest,
  DeviceReRegisterResult,
  LanAddressesResponse,
  SettingsApi,
} from "./api.ts";

/**
 * Feature flags the shared UI reads. Adapters set these; components do not branch on OS.
 */
export type PlatformCapabilities = {
  /** Edit an existing theme definition. Selection stays separate. */
  themeEdit: boolean;
  /** Add a new theme definition. Selection stays separate. */
  themeAdd: boolean;
  /** Transfers history screen and nav. */
  transferHistory: boolean;
  /** Backend or admin transfer log screens. */
  transferLogs: boolean;
  /** Warp background effect. */
  warp: boolean;
};

export type SharedFileItem = {
  uri: string;
  name: string;
  size: number;
};

export type Platform = {
  readonly env: {
    readonly sep: string;
    readonly homedir: string;
  };
  readonly capabilities: PlatformCapabilities;
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
  /**
   * Subscribe to native share payloads. Returns unsubscribe.
   */
  onSharedFiles?: (cb: (files: SharedFileItem[]) => void) => () => void;
};
