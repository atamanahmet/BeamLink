import { describe, expect, it, vi } from "vitest";
import { saveSpeedCapAction } from "./speedCapActions.ts";
import type { SpeedCapState } from "./types.ts";

function baseCap(patch: Partial<SpeedCapState> = {}): SpeedCapState {
  return { value: "80", save: "idle", error: null, correlationId: null, ...patch };
}

describe("saveSpeedCapAction", () => {
  it("reads problem detail on save failure", async () => {
    const setSpeedCap = vi.fn();
    await saveSpeedCapAction(
      {
        putSpeedCap: vi.fn().mockRejectedValue({
          response: {
            status: 400,
            data: {
              detail: "Speed limit must be null or a positive Mb/s value",
              code: "INVALID_SPEED_LIMIT",
              correlationId: "c-cap",
            },
          },
        }),
      } as never,
      baseCap(),
      setSpeedCap,
    );
    const updater = setSpeedCap.mock.calls.at(-1)?.[0] as (
      prev: SpeedCapState,
    ) => SpeedCapState;
    const next = updater(baseCap());
    expect(next.error).toBe("Speed limit must be null or a positive Mb/s value");
    expect(next.correlationId).toBe("c-cap");
    expect(next.save).toBe("error");
  });
});
