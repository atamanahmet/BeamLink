import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    projects: [
      "packages/core",
      "packages/ui",
      "packages/features",
      "packages/electron-main",
      "apps/android",
    ],
  },
});
