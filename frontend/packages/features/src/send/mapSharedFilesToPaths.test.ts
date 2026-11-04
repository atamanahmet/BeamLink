import { describe, expect, it } from "vitest";
import { mapSharedFilesToPaths } from "./mapSharedFilesToPaths.ts";

describe("mapSharedFilesToPaths", () => {
  it("keeps non empty uris in order", () => {
    expect(
      mapSharedFilesToPaths([
        { uri: "content://a", name: "a.txt", size: 1 },
        { uri: "content://b", name: "b.txt", size: 2 },
      ]),
    ).toEqual(["content://a", "content://b"]);
  });

  it("drops blank uris", () => {
    expect(
      mapSharedFilesToPaths([
        { uri: "  ", name: "x", size: 0 },
        { uri: "content://ok", name: "ok", size: 3 },
      ]),
    ).toEqual(["content://ok"]);
  });
});
