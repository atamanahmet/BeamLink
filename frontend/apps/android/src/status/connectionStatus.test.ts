import { describe, expect, test } from "vitest";
import { connectionStatusWords, phoneNeedsConnect } from "./connectionStatus.ts";

describe("connectionStatusWords", () => {
  test("connected when pin set and tokens or APPROVED", () => {
    expect(connectionStatusWords("APPROVED", false, true)).toBe("Connected");
    expect(connectionStatusWords("error", true, true)).toBe("Connected");
  });

  test("not connected when pin is missing", () => {
    expect(connectionStatusWords("APPROVED", true, false)).toBe("Not connected");
  });

  test("waiting while pending or registering", () => {
    expect(connectionStatusWords("PENDING", false, true)).toBe(
      "Waiting for approval",
    );
    expect(connectionStatusWords("registering", false, true)).toBe(
      "Waiting for approval",
    );
  });

  test("not connected otherwise", () => {
    expect(connectionStatusWords("not_configured", false, true)).toBe(
      "Not connected",
    );
    expect(connectionStatusWords("error", false, true)).toBe("Not connected");
    expect(connectionStatusWords(undefined, false, true)).toBe("Not connected");
  });
});

describe("phoneNeedsConnect", () => {
  test("true when not connected", () => {
    expect(phoneNeedsConnect("not_configured", false, false)).toBe(true);
    expect(phoneNeedsConnect("REJECTED", false, true)).toBe(true);
  });

  test("false when connected or waiting", () => {
    expect(phoneNeedsConnect("APPROVED", true, true)).toBe(false);
    expect(phoneNeedsConnect("PENDING", false, true)).toBe(false);
  });
});
