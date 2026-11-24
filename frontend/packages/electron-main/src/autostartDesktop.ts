/**
 * Quotes an AppImage path for a desktop Exec line.
 */
export function quoteDesktopExecPath(appImagePath: string): string {
  const escaped = appImagePath
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/`/g, "\\`")
    .replace(/\$/g, "\\$")
    .replace(/%/g, "%%");
  return `"${escaped}"`;
}

/**
 * Builds the Exec line for a Linux autostart desktop file.
 */
export function buildDesktopExecLine(appImagePath: string): string {
  return `Exec=${quoteDesktopExecPath(appImagePath)} --hidden`;
}

/**
 * Builds a full .desktop file for autostart.
 */
export function buildDesktopFileText(opts: {
  name: string;
  appImagePath: string;
}): string {
  return [
    "[Desktop Entry]",
    "Type=Application",
    `Name=${opts.name}`,
    buildDesktopExecLine(opts.appImagePath),
    "Terminal=false",
    "X-GNOME-Autostart-enabled=true",
    "",
  ].join("\n");
}

/**
 * Reads whether a desktop file enables autostart.
 */
export function isDesktopAutostartEnabled(content: string): boolean {
  const match = content.match(/^X-GNOME-Autostart-enabled=(.*)$/m);
  if (!match) {
    return true;
  }
  return match[1].trim() !== "false";
}

/**
 * Reads the Exec value from a desktop file, or null when missing.
 */
export function parseDesktopExec(content: string): string | null {
  const match = content.match(/^Exec=(.*)$/m);
  if (!match) {
    return null;
  }
  return match[1].trim();
}

/**
 * True when the desktop Exec matches the current AppImage with --hidden.
 */
export function desktopExecMatches(
  content: string,
  appImagePath: string,
): boolean {
  const exec = parseDesktopExec(content);
  if (exec == null) {
    return false;
  }
  return exec === `${quoteDesktopExecPath(appImagePath)} --hidden`;
}
