import type { ThemeFile } from "./types.ts";

const themeModules = import.meta.glob<{ default: ThemeFile }>("./themes/*.json", {
  eager: true,
});

/**
 * Loads every theme JSON in the themes folder.
 */
export function loadThemes(): ThemeFile[] {
  return Object.values(themeModules).map((mod) => mod.default);
}
