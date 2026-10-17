import type { Platform } from "@beamlink/core";

export type FsEntry = {
  name: string;
  fullPath: string;
  isDirectory: boolean;
  size?: number;
  locked: boolean;
  hidden: boolean;
};

export type SidebarItem = {
  label: string;
  path: string;
  icon: string;
};

export type FileBrowserModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (paths: string[]) => void;
  title?: string;
  /**
   * "multi"  — default, files + folders, multi-select (existing behaviour)
   * "folder" — single folder picker for directory selection
   */
  mode?: "multi" | "folder";
};

/**
 * Builds the sidebar shortcut list from the user home directory.
 */
export function getSidebarItems(platform: Platform): SidebarItem[] {
  const home = platform.env.homedir;
  const sep = platform.env.sep;
  const join = (...parts: string[]) => parts.join(sep).replace(/[/\\]+/g, sep);

  return [
    { label: "Desktop", icon: "🖥", path: join(home, "Desktop") },
    { label: "Downloads", icon: "⬇", path: join(home, "Downloads") },
    { label: "Documents", icon: "📄", path: join(home, "Documents") },
    { label: "Pictures", icon: "🖼", path: join(home, "Pictures") },
    { label: "Music", icon: "♪", path: join(home, "Music") },
    { label: "Videos", icon: "▶", path: join(home, "Videos") },
  ];
}

/**
 * Formats a byte size for the file list.
 */
export function formatSize(bytes?: number): string {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024)
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/**
 * Picks an emoji icon from a file name extension.
 */
export function getFileIcon(name: string): string {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    png: "🖼",
    jpg: "🖼",
    jpeg: "🖼",
    gif: "🖼",
    webp: "🖼",
    svg: "🖼",
    ico: "🖼",
    mp4: "🎬",
    mkv: "🎬",
    avi: "🎬",
    mov: "🎬",
    wmv: "🎬",
    mp3: "🎵",
    wav: "🎵",
    flac: "🎵",
    ogg: "🎵",
    aac: "🎵",
    zip: "📦",
    rar: "📦",
    "7z": "📦",
    tar: "📦",
    gz: "📦",
    ts: "📝",
    tsx: "📝",
    js: "📝",
    jsx: "📝",
    py: "📝",
    java: "📝",
    json: "📝",
    xml: "📝",
    yaml: "📝",
    yml: "📝",
    pdf: "📕",
    doc: "📄",
    docx: "📄",
    txt: "📄",
    md: "📄",
    xls: "📊",
    xlsx: "📊",
    csv: "📊",
    exe: "⚙",
    msi: "⚙",
    dmg: "⚙",
    sh: "⚙",
    bat: "⚙",
  };
  return map[ext] ?? "📄";
}
