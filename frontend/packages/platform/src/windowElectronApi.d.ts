export {};

declare global {
  interface Window {
    electronAPI: {
      selectFilesAndDirectories: () => Promise<string[]>;
      getPathForFiles: (files: File[]) => string[];
      onBackendStatus: (
        cb: (data: {
          status: "starting" | "ready" | "error";
          backendUrl?: string;
          message?: string;
          attempt?: number;
          max?: number;
        }) => void,
      ) => void;
      getHomedir: () => Promise<string>;
      getSep: () => string;
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
      /** Creates a single directory (non-recursive). Throws an error code on failure. */
      mkdir: (dirPath: string) => Promise<void>;
      /** Returns true if path exists on disk. */
      exists: (dirPath: string) => Promise<boolean>;
      onBackendLog: (cb: (line: string) => void) => void;
      getBackendStatus: () => Promise<{
        status: "starting" | "ready" | "error";
        backendUrl?: string;
        message?: string;
      }>;
      setWindowTheme: (colors: {
        background: string;
        foreground: string;
      }) => Promise<void>;
      showWindow?: () => Promise<{ ok: boolean; code?: string }>;
      saveAuthToken?: (
        token: string,
      ) => Promise<{ ok: true } | { ok: false; code: string }>;
      loadAuthToken?: () => Promise<
        { ok: true; data: string | null } | { ok: false; code: string }
      >;
      clearAuthToken?: () => Promise<
        { ok: true } | { ok: false; code: string }
      >;
    };
  }
}
