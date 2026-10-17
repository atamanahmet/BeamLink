import { describe, expect, test } from "vitest";
import { approach, stepWarpStar } from "./warpStarMath.ts";

describe("stepWarpStar", () => {
  test("moves z toward camera when speed is positive", () => {
    const next = stepWarpStar(
      { x: 0, y: 0, z: 100 },
      10,
      800,
      600,
      () => 0.5,
    );
    expect(next.z).toBe(90);
  });

  test("respawns when z passes zero", () => {
    const next = stepWarpStar(
      { x: 0, y: 0, z: 5 },
      10,
      800,
      600,
      () => 0.25,
    );
    expect(next.z).toBe(800);
    expect(next.x).toBe((0.25 - 0.5) * 800);
    expect(next.y).toBe((0.25 - 0.5) * 600);
  });
});

describe("approach", () => {
  test("moves toward target by factor", () => {
    expect(approach(0, 10, 0.5)).toBe(5);
  });
});
