import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.beamlink.peer",
  appName: "BeamLink Peer",
  webDir: "dist",
  server: {
    androidScheme: "https",
  },
  android: {
    adjustMarginsForEdgeToEdge: "disable",
  },
};

export default config;
