import { describe, expect, test } from "vitest";
import {
  mapErrorReason,
  parseProblem,
  problemFromUnknown,
} from "@beamlink/core";

describe("parseProblem", () => {
  test("reads a valid body", () => {
    expect(
      parseProblem(
        {
          title: "Bad Gateway",
          detail: "Target peer unreachable",
          code: "TARGET_UNREACHABLE",
          reason: "REFUSED",
          correlationId: "abc-123",
          status: 502,
        },
        502,
      ),
    ).toEqual({
      title: "Bad Gateway",
      detail: "Target peer unreachable",
      code: "TARGET_UNREACHABLE",
      reason: "REFUSED",
      correlationId: "abc-123",
      status: 502,
    });
  });

  test("missing fields stay null", () => {
    expect(parseProblem({}, 409)).toEqual({
      title: null,
      detail: null,
      code: null,
      reason: null,
      correlationId: null,
      status: 409,
    });
  });

  test("empty body stays blank", () => {
    expect(parseProblem("", 500)).toEqual({
      title: null,
      detail: null,
      code: null,
      reason: null,
      correlationId: null,
      status: 500,
    });
    expect(parseProblem(null, 500).correlationId).toBeNull();
  });

  test("non-JSON body stays blank", () => {
    expect(parseProblem("not json", 400)).toEqual({
      title: null,
      detail: null,
      code: null,
      reason: null,
      correlationId: null,
      status: 400,
    });
  });

  test("network error with no response", () => {
    expect(parseProblem(undefined, undefined)).toEqual({
      title: null,
      detail: null,
      code: null,
      reason: null,
      correlationId: null,
      status: null,
    });
    expect(problemFromUnknown(new Error("network down"))).toEqual({
      title: null,
      detail: null,
      code: null,
      reason: null,
      correlationId: null,
      status: null,
    });
  });
});

describe("mapErrorReason", () => {
  test("maps each reason", () => {
    expect(mapErrorReason("TIMEOUT")).toBe("The request timed out.");
    expect(mapErrorReason("CONNECT_TIMEOUT")).toBe(
      "Could not reach the other device. If it runs Windows, its firewall may be blocking BeamLink. Allow BeamLink on Private networks (needs an admin once).",
    );
    expect(mapErrorReason("REFUSED")).toBe("The connection was refused.");
    expect(mapErrorReason("UNKNOWN_HOST")).toBe("The host could not be found.");
    expect(mapErrorReason("TLS_TRUST")).toBe(
      "TLS handshake with the peer failed",
    );
    expect(mapErrorReason("TLS_OTHER")).toBe(
      "TLS handshake with the peer failed",
    );
    expect(mapErrorReason("UNKNOWN")).toBe("Something went wrong.");
  });

  test("unknown or empty reason is null", () => {
    expect(mapErrorReason("NOPE")).toBeNull();
    expect(mapErrorReason("")).toBeNull();
    expect(mapErrorReason(null)).toBeNull();
    expect(mapErrorReason(undefined)).toBeNull();
  });
});
