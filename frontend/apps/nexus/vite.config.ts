import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import electron from "vite-plugin-electron";
import renderer from "vite-plugin-electron-renderer";

const PACKAGED_CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src http://localhost:9090 http://localhost:7472; img-src 'self' data:; font-src 'self' data:; object-src 'none'; base-uri 'none'; form-action 'none'";

/**
 * Injects a CSP meta tag for the packaged renderer only.
 */
function packagedCspMeta(): Plugin {
  return {
    name: "packaged-csp-meta",
    apply: "build",
    transformIndexHtml(html) {
      return html.replace(
        "<head>",
        `<head>\n    <meta http-equiv="Content-Security-Policy" content="${PACKAGED_CSP}">`,
      );
    },
  };
}

export default defineConfig({
  plugins: [
    react(),
    packagedCspMeta(),
    electron([
      {
        /**
         * Main process entry.
         * Plugin compiles this and restarts Electron on change.
         */
        entry: "electron/main.ts",
        vite: {
          build: {
            outDir: "dist-electron",
            sourcemap: false,
          },
        },
      },
      {
        /**
         * Preload runs in renderer context with Node access.
         * Needs its own build target, sandboxed, not the main bundle.
         */
        entry: "electron/preload.ts",
        onstart(args) {
          args.reload();
        },
        vite: {
          build: {
            outDir: "dist-electron",
            sourcemap: false,
          },
        },
      },
    ]),
    renderer(),
  ],
  base: "./",
  build: {
    sourcemap: false,
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": "http://localhost:7472",
      "/actuator": "http://localhost:7472",
    },
  },
});
