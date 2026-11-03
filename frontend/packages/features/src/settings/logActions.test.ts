import { describe, expect, it, vi } from "vitest";
import { openLogsAction } from "./logActions.ts";
import type { LogState } from "./types.ts";

describe("openLogsAction", () => {
  it("reads problem detail on open failure", async () => {
    const setLog = vi.fn();
    await openLogsAction(
      {
        openLogs: vi.fn().mockRejectedValue({
          response: {
            status: 500,
            data: {
              detail: "System operation failed",
              code: "SYSTEM_ERROR",
              correlationId: "c-log",
            },
          },
        }),
      } as never,
      setLog,
    );
    const last = setLog.mock.calls.at(-1)?.[0] as LogState;
    expect(last.error).toBe("System operation failed");
    expect(last.correlationId).toBe("c-log");
    expect(last.opening).toBe(false);
  });
});
