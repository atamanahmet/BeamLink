import { describe, expect, test } from "vitest";
import { mapTransferErrorCode } from "./transferErrors.ts";

describe("mapTransferErrorCode", () => {
  test("maps TRANSFER_PAUSED", () => {
    expect(mapTransferErrorCode("TRANSFER_PAUSED")).toBe("Transfer is paused");
  });

  test("maps TRANSFER_ALREADY_PAUSED", () => {
    expect(mapTransferErrorCode("TRANSFER_ALREADY_PAUSED")).toBe(
      "Already paused",
    );
  });

  test("maps RESUME_FORBIDDEN", () => {
    expect(mapTransferErrorCode("RESUME_FORBIDDEN")).toBe("Resume not allowed");
  });

  test("maps PEER_UNREACHABLE", () => {
    expect(mapTransferErrorCode("PEER_UNREACHABLE")).toBe("Peer unreachable");
  });

  test("maps INVALID_RESUME_POLICY", () => {
    expect(mapTransferErrorCode("INVALID_RESUME_POLICY")).toBe(
      "Invalid resume policy",
    );
  });

  test("maps anything else", () => {
    expect(mapTransferErrorCode("OTHER")).toBe("Transfer action failed");
    expect(mapTransferErrorCode(undefined)).toBe("Transfer action failed");
  });
});
