import { describe, expect, it, vi } from "vitest";
import { saveResumePolicyAction } from "./resumePolicyActions.ts";
import type { ResumePolicyState } from "./types.ts";

function basePolicy(
  patch: Partial<ResumePolicyState> = {},
): ResumePolicyState {
  return {
    value: "OWNER_ONLY",
    save: "idle",
    error: null,
    correlationId: null,
    ...patch,
  };
}

describe("saveResumePolicyAction", () => {
  it("reads problem detail on save failure", async () => {
    const setResumePolicy = vi.fn();
    await saveResumePolicyAction(
      {
        putResumePolicy: vi.fn().mockRejectedValue({
          response: {
            status: 400,
            data: {
              detail: "resumePolicy must be OWNER_ONLY or ANYONE",
              code: "INVALID_RESUME_POLICY",
              correlationId: "c-rp",
            },
          },
        }),
      } as never,
      basePolicy(),
      setResumePolicy,
    );
    const updater = setResumePolicy.mock.calls.at(-1)?.[0] as (
      prev: ResumePolicyState,
    ) => ResumePolicyState;
    const next = updater(basePolicy());
    expect(next.error).toBe("resumePolicy must be OWNER_ONLY or ANYONE");
    expect(next.correlationId).toBe("c-rp");
    expect(next.save).toBe("error");
  });
});
