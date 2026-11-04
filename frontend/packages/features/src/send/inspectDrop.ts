import { BIG_DROP_FILE_LIMIT } from "@beamlink/core";

export type DropScan = {
  names: string[];
  nameCount: number;
  hasFolder: boolean;
  folderCount: number;
  fileCount: number;
  truncated: boolean;
  totalSize: number | null;
};

export type ScanDropOptions = {
  limit?: number;
  isCancelled?: () => boolean;
  yieldFn?: () => Promise<void>;
};

const YIELD_EVERY = 100;

/**
 * Reads FileSystemEntry list from a drop. Must run before any await.
 */
export function captureDropEntries(
  dataTransfer: DataTransfer,
): FileSystemEntry[] {
  const entries: FileSystemEntry[] = [];
  const items = dataTransfer.items;
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    if (!item || item.kind !== "file") {
      continue;
    }
    const entry = item.webkitGetAsEntry();
    if (entry) {
      entries.push(entry);
    }
  }
  return entries;
}

/**
 * Counts a FileList when FileSystemEntry is missing.
 */
export function scanFileList(files: File[]): DropScan {
  const truncated = files.length > BIG_DROP_FILE_LIMIT;
  const names = files.slice(0, 3).map((file) => file.name);
  let totalSize: number | null = null;
  if (!truncated) {
    let sum = 0;
    for (const file of files) {
      sum += file.size;
    }
    totalSize = sum;
  }
  return {
    names,
    nameCount: files.length,
    hasFolder: false,
    folderCount: 0,
    fileCount: truncated ? BIG_DROP_FILE_LIMIT + 1 : files.length,
    truncated,
    totalSize,
  };
}

/**
 * Counts dropped entries. Stops after limit + 1 files. Yields between batches.
 */
export async function scanDropEntries(
  entries: FileSystemEntry[],
  options: ScanDropOptions = {},
): Promise<DropScan> {
  const limit = options.limit ?? BIG_DROP_FILE_LIMIT;
  const isCancelled = options.isCancelled ?? (() => false);
  const yieldFn = options.yieldFn ?? defaultYield;
  const folderCount = countTopFolders(entries);
  const scan: MutableScan = {
    names: entries.slice(0, 3).map((entry) => entry.name),
    nameCount: entries.length,
    hasFolder: folderCount > 0,
    folderCount,
    fileCount: 0,
    truncated: false,
    totalSize: 0,
    processedSinceYield: 0,
  };
  const queue: FileSystemEntry[] = [...entries];
  let index = 0;

  while (index < queue.length && !scan.truncated) {
    if (isCancelled()) {
      return cancelledScan();
    }
    const entry = queue[index];
    index += 1;
    if (!entry) {
      continue;
    }
    if (entry.isDirectory) {
      await readDirectory(
        entry as FileSystemDirectoryEntry,
        queue,
        scan,
        limit,
        isCancelled,
        yieldFn,
      );
      continue;
    }
    if (entry.isFile) {
      await countFile(
        entry as FileSystemFileEntry,
        scan,
        limit,
        yieldFn,
      );
    }
  }

  return {
    names: scan.names,
    nameCount: scan.nameCount,
    hasFolder: scan.hasFolder,
    folderCount: scan.folderCount,
    fileCount: scan.fileCount,
    truncated: scan.truncated,
    totalSize: scan.truncated ? null : scan.totalSize,
  };
}

type MutableScan = {
  names: string[];
  nameCount: number;
  hasFolder: boolean;
  folderCount: number;
  fileCount: number;
  truncated: boolean;
  totalSize: number;
  processedSinceYield: number;
};

/**
 * Walks one directory in readEntries batches and stops after the file limit.
 */
async function readDirectory(
  dir: FileSystemDirectoryEntry,
  queue: FileSystemEntry[],
  scan: MutableScan,
  limit: number,
  isCancelled: () => boolean,
  yieldFn: () => Promise<void>,
): Promise<void> {
  const reader = dir.createReader();
  for (;;) {
    if (isCancelled() || scan.truncated) {
      return;
    }
    const batch = await readEntryBatch(reader);
    if (batch.length === 0) {
      return;
    }
    for (const child of batch) {
      if (scan.truncated) {
        return;
      }
      if (child.isDirectory) {
        queue.push(child);
        continue;
      }
      if (child.isFile) {
        await countFile(child as FileSystemFileEntry, scan, limit, yieldFn);
      }
    }
    await yieldFn();
  }
}

/**
 * Adds one file to the count. Skips size after the limit is passed.
 */
async function countFile(
  entry: FileSystemFileEntry,
  scan: MutableScan,
  limit: number,
  yieldFn: () => Promise<void>,
): Promise<void> {
  scan.fileCount += 1;
  if (scan.fileCount > limit) {
    scan.truncated = true;
    return;
  }
  scan.totalSize += await readFileSize(entry);
  scan.processedSinceYield += 1;
  if (scan.processedSinceYield >= YIELD_EVERY) {
    scan.processedSinceYield = 0;
    await yieldFn();
  }
}

/**
 * Reads one readEntries batch. Empty on error so the walk can stop that dir.
 */
async function readEntryBatch(
  reader: FileSystemDirectoryReader,
): Promise<FileSystemEntry[]> {
  try {
    return await new Promise((resolve, reject) => {
      reader.readEntries(resolve, reject);
    });
  } catch {
    return [];
  }
}

/**
 * Reads size for one file entry. Missing size counts as 0.
 */
async function readFileSize(entry: FileSystemFileEntry): Promise<number> {
  try {
    const file = await new Promise<File>((resolve, reject) => {
      entry.file(resolve, reject);
    });
    return file.size;
  } catch {
    return 0;
  }
}

/**
 * Lets the UI paint between batches.
 */
function defaultYield(): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, 0);
  });
}

/**
 * Counts top-level directories in the drop.
 */
function countTopFolders(entries: FileSystemEntry[]): number {
  let count = 0;
  for (const entry of entries) {
    if (entry.isDirectory) {
      count += 1;
    }
  }
  return count;
}

/**
 * Dummy result for a scan that was replaced by a newer drop.
 */
function cancelledScan(): DropScan {
  return {
    names: [],
    nameCount: 0,
    hasFolder: false,
    folderCount: 0,
    fileCount: 0,
    truncated: false,
    totalSize: null,
  };
}
