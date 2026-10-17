import { hexToRgb, type Rgb } from "./hexToRgb.ts";
import {
  approach,
  stepWarpStar,
  type WarpStar,
} from "./warpStarMath.ts";

export type WarpColors = {
  trailColor: string;
  coreColor: string;
};

export type WarpEngine = {
  start: () => void;
  stop: () => void;
  setColors: (trailColor: string, coreColor: string) => void;
  resize: () => void;
  destroy: () => void;
};

const MAX_SPEED = 10;
const SHIMMER_MIN = 0.3;
const SHIMMER_MAX = 1.0;
const SPRITE_LEVELS = 16;
const SPRITE_RADIUS = 2;
const SPRITE_SIZE = SPRITE_RADIUS * 4;

/**
 * Builds one soft glow sprite for a quantized shimmer level.
 */
function buildSprite(core: Rgb, trail: Rgb, shimmer: number): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = SPRITE_SIZE;
  canvas.height = SPRITE_SIZE;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    return canvas;
  }
  const cx = SPRITE_SIZE / 2;
  const cy = SPRITE_SIZE / 2;
  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, SPRITE_RADIUS * 2);
  gradient.addColorStop(
    0,
    `rgba(${core.r}, ${core.g}, ${core.b}, ${shimmer})`,
  );
  gradient.addColorStop(
    0.5,
    `rgba(${trail.r},${trail.g},${trail.b},${shimmer * 0.6})`,
  );
  gradient.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = gradient;
  ctx.beginPath();
  ctx.arc(cx, cy, SPRITE_RADIUS, 0, Math.PI * 2);
  ctx.fill();
  return canvas;
}

/**
 * Maps shimmer to a sprite bucket index.
 */
function shimmerLevel(shimmer: number): number {
  const t = (shimmer - SHIMMER_MIN) / (SHIMMER_MAX - SHIMMER_MIN);
  return Math.max(
    0,
    Math.min(SPRITE_LEVELS - 1, Math.round(t * (SPRITE_LEVELS - 1))),
  );
}

/**
 * Plain canvas warp engine. No React.
 */
export function createWarp(
  canvas: HTMLCanvasElement,
  options: WarpColors,
): WarpEngine {
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("2d context missing");
  }

  let active = false;
  let destroyed = false;
  let running = false;
  let animationId = 0;
  let cssWidth = 0;
  let cssHeight = 0;
  let stars: WarpStar[] = [];
  let trailRgb = hexToRgb(options.trailColor);
  let coreRgb = hexToRgb(options.coreColor);
  let sprites: HTMLCanvasElement[] = [];
  let currentSpeed = 0;
  let visualWarp = 0;
  let shimmerPhase = 0;

  const motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  let reducedMotion = motionQuery.matches;

  /**
   * Rebuilds glow sprites for the current colors.
   */
  const rebuildSprites = () => {
    sprites = [];
    for (let i = 0; i < SPRITE_LEVELS; i++) {
      const t = i / (SPRITE_LEVELS - 1);
      const shimmer = SHIMMER_MIN + t * (SHIMMER_MAX - SHIMMER_MIN);
      sprites.push(buildSprite(coreRgb, trailRgb, shimmer));
    }
  };

  /**
   * Rebuilds star list for the current CSS size.
   */
  const rebuildStars = () => {
    const count = Math.floor((cssWidth * cssHeight) / 4000);
    stars = [];
    for (let i = 0; i < count; i++) {
      stars.push({
        x: (Math.random() - 0.5) * cssWidth,
        y: (Math.random() - 0.5) * cssHeight,
        z: Math.random() * cssWidth,
      });
    }
  };

  /**
   * Applies devicePixelRatio capped at 2 and keeps CSS size.
   */
  const applySize = () => {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    cssWidth = window.innerWidth;
    cssHeight = window.innerHeight;
    canvas.style.width = `${cssWidth}px`;
    canvas.style.height = `${cssHeight}px`;
    canvas.width = Math.max(1, Math.floor(cssWidth * dpr));
    canvas.height = Math.max(1, Math.floor(cssHeight * dpr));
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  };

  const wake = () => {
    if (destroyed || running) {
      return;
    }
    running = true;
    animationId = requestAnimationFrame(animate);
  };

  const animate = () => {
    if (destroyed) {
      running = false;
      return;
    }

    const targetSpeed = active && !reducedMotion ? MAX_SPEED : 0;
    currentSpeed = approach(currentSpeed, targetSpeed, 0.08);
    const rawWarp = currentSpeed / MAX_SPEED;
    visualWarp = approach(visualWarp, rawWarp, 0.04);
    const warpFactor = visualWarp;

    if (!active && currentSpeed < 0.01) {
      currentSpeed = 0;
    }

    ctx.clearRect(0, 0, cssWidth, cssHeight);

    for (let i = 0; i < stars.length; i++) {
      const star = stepWarpStar(
        stars[i],
        currentSpeed > 0 ? currentSpeed : 0,
        cssWidth,
        cssHeight,
        Math.random,
      );
      stars[i] = star;

      const k = 128.0 / star.z;
      const x = star.x * k + cssWidth / 2;
      const y = star.y * k + cssHeight / 2;
      const stretch = visualWarp * MAX_SPEED * 2;
      const px = star.x * (128.0 / (star.z + stretch)) + cssWidth / 2;
      const py = star.y * (128.0 / (star.z + stretch)) + cssHeight / 2;

      if (warpFactor > 0.001) {
        ctx.strokeStyle = `rgba(${trailRgb.r},${trailRgb.g},${trailRgb.b},${0.8 * warpFactor})`;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(px, py);
        ctx.lineTo(x, y);
        ctx.stroke();
      }

      shimmerPhase += 0.00005;
      if (shimmerPhase > Math.PI * 2) {
        shimmerPhase -= Math.PI * 2;
      }

      const offset = (star.x + star.y) * 0.5;
      const shimmer =
        SHIMMER_MIN +
        (SHIMMER_MAX - SHIMMER_MIN) *
          (0.5 + 0.5 * Math.sin(shimmerPhase + offset));
      const radius = 1 + Math.random() * 1.0;
      const sprite = sprites[shimmerLevel(shimmer)];
      if (sprite) {
        const drawSize = radius * 4;
        ctx.drawImage(
          sprite,
          x - drawSize / 2,
          y - drawSize / 2,
          drawSize,
          drawSize,
        );
      }
    }

    if (
      (!active || reducedMotion) &&
      currentSpeed < 0.01 &&
      visualWarp < 0.001
    ) {
      currentSpeed = 0;
      visualWarp = 0;
      running = false;
      return;
    }

    animationId = requestAnimationFrame(animate);
  };

  const onMotionChange = () => {
    reducedMotion = motionQuery.matches;
    wake();
  };

  const onWindowResize = () => {
    engine.resize();
  };

  rebuildSprites();
  applySize();
  rebuildStars();
  motionQuery.addEventListener("change", onMotionChange);
  window.addEventListener("resize", onWindowResize);
  wake();

  const engine: WarpEngine = {
    start: () => {
      if (destroyed) {
        return;
      }
      active = true;
      wake();
    },
    stop: () => {
      if (destroyed) {
        return;
      }
      active = false;
      wake();
    },
    setColors: (trailColor, coreColor) => {
      if (destroyed) {
        return;
      }
      trailRgb = hexToRgb(trailColor);
      coreRgb = hexToRgb(coreColor);
      rebuildSprites();
      wake();
    },
    resize: () => {
      if (destroyed) {
        return;
      }
      applySize();
      rebuildStars();
      wake();
    },
    destroy: () => {
      if (destroyed) {
        return;
      }
      destroyed = true;
      running = false;
      cancelAnimationFrame(animationId);
      window.removeEventListener("resize", onWindowResize);
      motionQuery.removeEventListener("change", onMotionChange);
      stars = [];
      sprites = [];
    },
  };

  return engine;
}
