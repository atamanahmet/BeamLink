import { registerPlugin } from "@capacitor/core";
import type { Platform, SharedFileItem } from "@beamlink/core";
import { Beamlink } from "../native/beamlinkPlugin.ts";

type ShareReceiverPlugin = {
  addListener(
    eventName: "sharedFiles",
    listenerFunc: (event: { files: SharedFileItem[] }) => void,
  ): Promise<{ remove: () => Promise<void> }>;
};

const ShareReceiver = registerPlugin<ShareReceiverPlugin>("ShareReceiver");

/**
 * Builds a Platform for the Capacitor Android shell.
 * Shared files stay as SharedFileItem refs (uri, name, size). No path mapping.
 */
export async function createAndroidPlatform(): Promise<Platform> {
  const nexus = await Beamlink.getNexusAddress().catch(() => ({ url: "" }));
  if (nexus.url) {
    localStorage.setItem("backendUrl", nexus.url);
  }

  return {
    env: { sep: "/", homedir: "/" },
    capabilities: {
      themeEdit: false,
      themeAdd: false,
      transferHistory: false,
      transferLogs: false,
      warp: false,
    },
    getPathForFiles: async (files) =>
      files.map((file) => file.name).filter(Boolean),
    getRoots: async () => [],
    readDir: async () => [],
    mkdir: async () => undefined,
    exists: async () => false,
    onBackendStatus: async (cb) => {
      cb({
        status: "ready",
        backendUrl: localStorage.getItem("backendUrl") ?? "",
      });
    },
    getBackendStatus: async () => ({
      status: "ready",
      backendUrl: localStorage.getItem("backendUrl") ?? "",
    }),
    onSharedFiles: (cb) => {
      let handle: { remove: () => Promise<void> } | null = null;
      void ShareReceiver.addListener("sharedFiles", (event) => {
        cb(event.files ?? []);
      }).then((h) => {
        handle = h;
      });
      return () => {
        void handle?.remove();
      };
    },
  };
}
