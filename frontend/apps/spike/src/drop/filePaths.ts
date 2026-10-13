export type DroppedFileInfo = {
  name: string;
  size: number;
  type: string;
  path: string;
};

type ElectronApi = {
  getPathForFiles: (files: File[]) => string[];
};

/**
 * Reads local OS paths for dropped files via the preload bridge.
 */
export function describeDroppedFiles(files: File[]): DroppedFileInfo[] {
  const api = (window as Window & { electronAPI?: ElectronApi }).electronAPI;
  const paths = api?.getPathForFiles(files) ?? [];
  return files.map((file, index) => ({
    name: file.name,
    size: file.size,
    type: file.type,
    path: paths[index] ?? "",
  }));
}
