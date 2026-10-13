import fs from "fs";

export type WindowThemeColors = {
  background: string;
  foreground: string;
};

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

/**
 * Returns true when the value is a 6 digit hex color.
 */
export function isHexColor(value: string): boolean {
  return HEX_COLOR.test(value);
}

/**
 * Reads saved window theme colors, or returns the fallback.
 */
export function readSavedWindowTheme(
  filePath: string,
  fallback: WindowThemeColors,
): WindowThemeColors {
  try {
    const raw = fs.readFileSync(filePath, "utf-8");
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") {
      return fallback;
    }
    const record = parsed as Record<string, unknown>;
    const background = record.background;
    const foreground = record.foreground;
    if (
      typeof background !== "string" ||
      typeof foreground !== "string" ||
      !isHexColor(background) ||
      !isHexColor(foreground)
    ) {
      return fallback;
    }
    return { background, foreground };
  } catch {
    return fallback;
  }
}

/**
 * Writes window theme colors to disk. Ignores write failures.
 */
export function saveWindowTheme(
  filePath: string,
  colors: WindowThemeColors,
): void {
  try {
    if (!isHexColor(colors.background) || !isHexColor(colors.foreground)) {
      return;
    }
    fs.writeFileSync(
      filePath,
      JSON.stringify({
        background: colors.background,
        foreground: colors.foreground,
      }),
      "utf-8",
    );
  } catch {}
}
