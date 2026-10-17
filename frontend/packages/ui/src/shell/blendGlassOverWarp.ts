import { formatHex, interpolate, parse } from "culori";
import { GLASS_SURFACE_ALPHA } from "./glass.ts";

/**
 * Blends glass over a warp color the same way CSS color-mix does.
 */
export function blendGlassOverWarp(
  glass: string,
  warpCore: string,
  alpha: number = GLASS_SURFACE_ALPHA,
): string {
  const glassColor = parse(glass);
  const warpColor = parse(warpCore);
  if (!glassColor || !warpColor) {
    throw new Error("blendGlassOverWarp needs parseable colors");
  }
  const mix = interpolate([glassColor, warpColor], "rgb");
  const hex = formatHex(mix(1 - alpha));
  if (!hex) {
    throw new Error("blendGlassOverWarp could not format the blend");
  }
  return hex;
}
