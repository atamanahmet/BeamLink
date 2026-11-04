import { describe, expect, test } from "vitest";
import { dispatchErrorMessage } from "./dispatchErrorMessage.ts";

describe("dispatchErrorMessage", () => {
  test("maps TARGET_UNREACHABLE code", () => {
    expect(
      dispatchErrorMessage({
        response: { data: { code: "TARGET_UNREACHABLE", detail: "long" } },
      }),
    ).toBe("Target peer is unreachable or has receive turned off");
  });

  test("CONNECT_TIMEOUT reason wins over TARGET_UNREACHABLE", () => {
    expect(
      dispatchErrorMessage({
        response: {
          data: {
            code: "TARGET_UNREACHABLE",
            detail: "Target peer unreachable",
            reason: "CONNECT_TIMEOUT",
          },
        },
      }),
    ).toBe(
      "Could not reach the other device. If it runs Windows, its firewall may be blocking BeamLink. Allow BeamLink on Private networks (needs an admin once).",
    );
  });

  test("CONNECT_TIMEOUT reason wins without code", () => {
    expect(
      dispatchErrorMessage({
        response: {
          data: {
            detail: "Target peer unreachable",
            reason: "CONNECT_TIMEOUT",
          },
        },
      }),
    ).toBe(
      "Could not reach the other device. If it runs Windows, its firewall may be blocking BeamLink. Allow BeamLink on Private networks (needs an admin once).",
    );
  });

  test("falls back to detail then axios text", () => {
    expect(
      dispatchErrorMessage({ response: { data: { detail: "Nope" } } }),
    ).toBe("Nope");
    expect(dispatchErrorMessage({ message: "Axios failed" })).toBe(
      "Axios failed",
    );
  });
});
