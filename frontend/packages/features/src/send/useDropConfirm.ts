import { useEffect, useRef, useState } from "react";
import { bigDropRule } from "@beamlink/core";
import { formatBigDropBody } from "./bigDropCopy.ts";
import {
  captureDropEntries,
  scanDropEntries,
  scanFileList,
  type DropScan,
} from "./inspectDrop.ts";

export type UseDropConfirmOptions = {
  getPathForFiles: (files: File[]) => Promise<string[]>;
  onAccept: (paths: string[]) => void | Promise<void>;
  peerName: string;
};

export type DropConfirmControls = {
  onDrop: (dataTransfer: DataTransfer) => Promise<void>;
  open: boolean;
  body: string;
  confirmPendingDrop: () => void;
  cancelPendingDrop: () => void;
};

type PendingDrop = {
  paths: string[];
  scan: DropScan;
};

/**
 * Confirms a drop when it has a folder or more than 1000 files.
 */
export function useDropConfirm(
  options: UseDropConfirmOptions,
): DropConfirmControls {
  const genRef = useRef(0);
  const pendingRef = useRef<PendingDrop | null>(null);
  const optionsRef = useRef(options);
  const [pending, setPending] = useState<PendingDrop | null>(null);

  useEffect(() => {
    optionsRef.current = options;
  }, [options]);

  const setPendingBoth = (value: PendingDrop | null) => {
    pendingRef.current = value;
    setPending(value);
  };

  const onDrop = async (dataTransfer: DataTransfer) => {
    const files = Array.from(dataTransfer.files);
    const entries = captureDropEntries(dataTransfer);
    if (files.length === 0 && entries.length === 0) {
      return;
    }
    const gen = ++genRef.current;
    setPendingBoth(null);

    const paths = await optionsRef.current.getPathForFiles(files);
    if (gen !== genRef.current) {
      return;
    }

    const scan =
      entries.length > 0
        ? await scanDropEntries(entries, {
            isCancelled: () => gen !== genRef.current,
          })
        : scanFileList(files);
    if (gen !== genRef.current) {
      return;
    }

    if (
      !bigDropRule({
        hasFolder: scan.hasFolder,
        fileCount: scan.fileCount,
      })
    ) {
      await optionsRef.current.onAccept(paths);
      return;
    }
    setPendingBoth({ paths, scan });
  };

  const confirmPendingDrop = () => {
    const current = pendingRef.current;
    setPendingBoth(null);
    if (!current) {
      return;
    }
    void optionsRef.current.onAccept(current.paths);
  };

  const cancelPendingDrop = () => {
    genRef.current += 1;
    setPendingBoth(null);
  };

  const body = pending
    ? formatBigDropBody({
        names: pending.scan.names,
        nameCount: pending.scan.nameCount,
        folderCount: pending.scan.folderCount,
        fileCount: pending.scan.fileCount,
        truncated: pending.scan.truncated,
        totalSize: pending.scan.totalSize,
        peerName: options.peerName,
      })
    : "";

  return {
    onDrop,
    open: pending !== null,
    body,
    confirmPendingDrop,
    cancelPendingDrop,
  };
}
