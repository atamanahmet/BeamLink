import { describe, expect, test } from "vitest";
import {
  mapTransferErrorCode,
  transferActionErrorMessage,
} from "./transferErrors.ts";

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

  test("maps TARGET_UNREACHABLE", () => {
    expect(mapTransferErrorCode("TARGET_UNREACHABLE")).toBe(
      "Target peer is unreachable or has receive turned off",
    );
  });

  test("maps INVALID_RESUME_POLICY", () => {
    expect(mapTransferErrorCode("INVALID_RESUME_POLICY")).toBe(
      "Invalid resume policy",
    );
  });

  test("maps CHECKSUM_MISMATCH", () => {
    expect(mapTransferErrorCode("CHECKSUM_MISMATCH")).toBe(
      "File was damaged during transfer, send it again",
    );
  });

  test("maps INSUFFICIENT_DISK", () => {
    expect(mapTransferErrorCode("INSUFFICIENT_DISK")).toBe(
      "Not enough disk space. Free some space, then resume.",
    );
  });

  test("maps anything else", () => {
    expect(mapTransferErrorCode("OTHER")).toBe("Transfer action failed");
    expect(mapTransferErrorCode(undefined)).toBe("Transfer action failed");
  });
});

describe("transferActionErrorMessage", () => {
  const hint =
    "Could not reach the other device. If it runs Windows, its firewall may be blocking BeamLink. Allow BeamLink on Private networks (needs an admin once).";

  test("CONNECT_TIMEOUT wins when code is present", () => {
    expect(
      transferActionErrorMessage(
        {
          response: {
            data: {
              code: "TARGET_UNREACHABLE",
              detail: "Target peer unreachable",
              reason: "CONNECT_TIMEOUT",
            },
          },
        },
        "fallback",
      ),
    ).toBe(hint);
  });

  test("CONNECT_TIMEOUT wins when code is absent", () => {
    expect(
      transferActionErrorMessage(
        {
          response: {
            data: {
              detail: "Target peer unreachable",
              reason: "CONNECT_TIMEOUT",
            },
          },
        },
        "fallback",
      ),
    ).toBe(hint);
  });

  test("other reason keeps code text", () => {
    expect(
      transferActionErrorMessage(
        {
          response: {
            data: {
              code: "TARGET_UNREACHABLE",
              detail: "Target peer unreachable",
              reason: "TIMEOUT",
            },
          },
        },
        "fallback",
      ),
    ).toBe("Target peer is unreachable or has receive turned off");
  });
});
