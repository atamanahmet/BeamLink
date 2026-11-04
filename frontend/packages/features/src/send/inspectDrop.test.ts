import { describe, expect, it } from "vitest";
import { BIG_DROP_FILE_LIMIT } from "@beamlink/core";
import { scanDropEntries, scanFileList } from "./inspectDrop.ts";

function fakeFile(name: string, size: number): FileSystemFileEntry {
  return {
    isFile: true,
    isDirectory: false,
    name,
    file: (success: (file: File) => void) => {
      success(new File([new Uint8Array(size)], name));
    },
  } as FileSystemFileEntry;
}

function fakeDir(
  name: string,
  batches: FileSystemEntry[][],
): FileSystemDirectoryEntry {
  let batchIndex = 0;
  return {
    isFile: false,
    isDirectory: true,
    name,
    createReader: () => ({
      readEntries: (success: (entries: FileSystemEntry[]) => void) => {
        if (batchIndex >= batches.length) {
          success([]);
          return;
        }
        const batch = batches[batchIndex] ?? [];
        batchIndex += 1;
        success(batch);
      },
    }),
  } as FileSystemDirectoryEntry;
}

const noYield = async () => {};

describe("scanDropEntries", () => {
  it("one file is not truncated and has size", async () => {
    const scan = await scanDropEntries([fakeFile("a.txt", 10)], {
      yieldFn: noYield,
    });
    expect(scan).toEqual({
      names: ["a.txt"],
      nameCount: 1,
      hasFolder: false,
      folderCount: 0,
      fileCount: 1,
      truncated: false,
      totalSize: 10,
    });
  });

  it("stops after limit + 1 and hides size", async () => {
    let reads = 0;
    const files = Array.from({ length: BIG_DROP_FILE_LIMIT + 50 }, (_, i) =>
      fakeFile(`f${i}.txt`, 1),
    );
    const first = files.slice(0, 100);
    const rest = files.slice(100);
    const dir = {
      isFile: false,
      isDirectory: true,
      name: "drive",
      createReader: () => ({
        readEntries: (success: (entries: FileSystemEntry[]) => void) => {
          reads += 1;
          if (reads === 1) {
            success(first);
            return;
          }
          if (reads === 2) {
            success(rest);
            return;
          }
          success([]);
        },
      }),
    } as FileSystemDirectoryEntry;

    const scan = await scanDropEntries([dir], { yieldFn: noYield });
    expect(scan.truncated).toBe(true);
    expect(scan.hasFolder).toBe(true);
    expect(scan.folderCount).toBe(1);
    expect(scan.fileCount).toBe(BIG_DROP_FILE_LIMIT + 1);
    expect(scan.totalSize).toBeNull();
    expect(reads).toBe(2);
  });

  it("one folder with 1 file", async () => {
    const scan = await scanDropEntries(
      [fakeDir("photos", [[fakeFile("x.jpg", 5)]])],
      { yieldFn: noYield },
    );
    expect(scan.hasFolder).toBe(true);
    expect(scan.folderCount).toBe(1);
    expect(scan.fileCount).toBe(1);
    expect(scan.truncated).toBe(false);
    expect(scan.totalSize).toBe(5);
  });
});

describe("scanFileList", () => {
  it("1000 files is not truncated", () => {
    const files = Array.from(
      { length: 1000 },
      (_, i) => new File([new Uint8Array(1)], `f${i}.txt`),
    );
    const scan = scanFileList(files);
    expect(scan.truncated).toBe(false);
    expect(scan.fileCount).toBe(1000);
    expect(scan.totalSize).toBe(1000);
    expect(scan.hasFolder).toBe(false);
  });

  it("1001 files is truncated and has no size", () => {
    const files = Array.from(
      { length: 1001 },
      (_, i) => new File([new Uint8Array(1)], `f${i}.txt`),
    );
    const scan = scanFileList(files);
    expect(scan.truncated).toBe(true);
    expect(scan.fileCount).toBe(1001);
    expect(scan.totalSize).toBeNull();
  });
});
