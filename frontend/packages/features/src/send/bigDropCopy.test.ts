import { describe, expect, it } from "vitest";
import { formatBigDropBody } from "./bigDropCopy.ts";

describe("formatBigDropBody", () => {
  it("names folders files and size", () => {
    expect(
      formatBigDropBody({
        names: ["a.txt", "b.txt"],
        nameCount: 2,
        folderCount: 0,
        fileCount: 2,
        truncated: false,
        totalSize: 2048,
        peerName: "Alice",
      }),
    ).toBe("Send to Alice?\na.txt, b.txt\nFolders: 0\nFiles: 2\nSize: 2.0 KB");
  });

  it("first 3 names then and N more", () => {
    expect(
      formatBigDropBody({
        names: ["a", "b", "c"],
        nameCount: 5,
        folderCount: 1,
        fileCount: 1001,
        truncated: true,
        totalSize: null,
        peerName: "Bob",
      }),
    ).toBe(
      "Send to Bob?\na, b, c and 2 more\nFolders: 1\nFiles: more than 1000 files",
    );
  });
});
