export type WarpStar = {
  x: number;
  y: number;
  z: number;
};

/**
 * Moves one star one frame. Respawn when it passes the camera.
 */
export function stepWarpStar(
  star: WarpStar,
  speed: number,
  width: number,
  height: number,
  rand: () => number,
): WarpStar {
  let z = star.z;
  if (speed > 0) {
    z -= speed;
  }
  if (z <= 0) {
    return {
      x: (rand() - 0.5) * width,
      y: (rand() - 0.5) * height,
      z: width,
    };
  }
  return {
    x: star.x + (rand() - 0.5) * 0.5,
    y: star.y + (rand() - 0.5) * 0.5,
    z,
  };
}

/**
 * Smoothly moves current toward target by factor.
 */
export function approach(current: number, target: number, factor: number): number {
  return current + (target - current) * factor;
}
