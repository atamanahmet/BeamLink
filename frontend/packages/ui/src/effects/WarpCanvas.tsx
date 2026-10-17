import { useEffect, useRef } from "react";
import { createWarp, type WarpEngine } from "./createWarp.ts";

export type WarpCanvasProps = {
  active: boolean;
  trailColor: string;
  coreColor: string;
};

/**
 * Thin React wrapper around the plain warp engine.
 */
export function WarpCanvas({
  active,
  trailColor,
  coreColor,
}: WarpCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const engineRef = useRef<WarpEngine | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }
    const engine = createWarp(canvas, { trailColor, coreColor });
    engineRef.current = engine;
    if (active) {
      engine.start();
    } else {
      engine.stop();
    }
    return () => {
      engine.destroy();
      engineRef.current = null;
    };
    // Engine is created once per mount. Colors and active are forwarded below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (active) {
      engineRef.current?.start();
    } else {
      engineRef.current?.stop();
    }
  }, [active]);

  useEffect(() => {
    engineRef.current?.setColors(trailColor, coreColor);
  }, [trailColor, coreColor]);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: "absolute",
        inset: 0,
        zIndex: 0,
        pointerEvents: "none",
      }}
    />
  );
}
