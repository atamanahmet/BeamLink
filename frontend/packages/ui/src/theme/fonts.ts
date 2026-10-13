export type FontEntry = {
  id: string;
  name: string;
  family: string;
  fallback: string;
};

export const DEFAULT_FONT_ID = "system-ui";

export const FONTS: readonly FontEntry[] = [
  {
    id: "system-ui",
    name: "System UI",
    family: "system-ui",
    fallback: "Segoe UI, Roboto, Helvetica, Arial, sans-serif",
  },
  {
    id: "system-mono",
    name: "System Mono",
    family: "ui-monospace",
    fallback:
      "SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, Courier New, monospace",
  },
] as const;

/**
 * Returns a known font id, or the default when the id is unknown.
 */
export function resolveFontId(id: unknown): string {
  if (typeof id !== "string") {
    return DEFAULT_FONT_ID;
  }
  return FONTS.some((font) => font.id === id) ? id : DEFAULT_FONT_ID;
}

/**
 * Resolves a font entry from an id, falling back to the default.
 */
export function resolveFont(id: unknown): FontEntry {
  const fontId = resolveFontId(id);
  const font = FONTS.find((entry) => entry.id === fontId);
  if (!font) {
    throw new Error(`Font registry is missing ${fontId}`);
  }
  return font;
}

/**
 * Builds a CSS font-family stack from a registry entry.
 */
export function fontFamilyStack(font: FontEntry): string {
  return `${font.family}, ${font.fallback}`;
}
