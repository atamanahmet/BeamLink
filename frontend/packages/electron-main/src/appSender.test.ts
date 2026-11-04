import { describe, expect, test } from "vitest";
import { isExactAppSenderUrl } from "./appSender.ts";

const packagedIndex = "file:///C:/app/dist/index.html";
const devOrigin = "http://localhost:5174";

describe("isExactAppSenderUrl", () => {
  test("allows the exact packaged index path", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "file:///C:/app/dist/index.html",
        packaged: true,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: true,
      }),
    ).toBe(true);
  });

  test("allows hash and query on the packaged index", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "file:///C:/app/dist/index.html#/upload?x=1",
        packaged: true,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: true,
      }),
    ).toBe(true);
  });

  test("rejects another file path", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "file:///C:/evil/index.html",
        packaged: true,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: true,
      }),
    ).toBe(false);
  });

  test("rejects a path with ..", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "file:///C:/app/dist/../secret/index.html",
        packaged: true,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: true,
      }),
    ).toBe(false);
  });

  test("rejects the dev origin when packaged", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "http://localhost:5174/",
        packaged: true,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: true,
      }),
    ).toBe(false);
  });

  test("allows the exact dev origin root", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "http://localhost:5174/",
        packaged: false,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: false,
      }),
    ).toBe(true);
    expect(
      isExactAppSenderUrl({
        senderUrl: "http://localhost:5174/#/upload",
        packaged: false,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: false,
      }),
    ).toBe(true);
  });

  test("rejects a sub path on the dev origin", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "http://localhost:5174/evil",
        packaged: false,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: false,
      }),
    ).toBe(false);
  });

  test("rejects garbage strings", () => {
    expect(
      isExactAppSenderUrl({
        senderUrl: "not a url",
        packaged: false,
        appIndexUrl: packagedIndex,
        devOrigin,
        caseInsensitivePath: false,
      }),
    ).toBe(false);
  });
});
