import type { NexusTlsPinAdapter } from "@beamlink/features";
import { Beamlink } from "../native/beamlinkPlugin.ts";

/**
 * Phone adapter for nexus TLS first contact. Native only here.
 */
export const androidNexusTlsPinAdapter: NexusTlsPinAdapter = {
  getStatus: async () => {
    const status = await Beamlink.getNexusTlsStatus();
    return {
      url: status.url,
      pinSet: status.pinSet,
      suggestedUrl: "",
      pendingFingerprint: status.pendingFingerprint,
    };
  },

  probe: async (ip, port) => {
    const result = await Beamlink.probeNexus({ url: `https://${ip}:${port}` });
    return { url: result.url, fingerprint: result.fingerprint };
  },

  confirm: async (ip, port, fingerprint) => {
    await Beamlink.confirmNexusPin({
      url: `https://${ip}:${port}`,
      fingerprint,
    });
  },

  cancel: async () => {},

  clearPin: async () => {
    await Beamlink.clearNexusPin();
  },
};
