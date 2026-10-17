import { useThemeTokens } from "../mantine/useThemeTokens.ts";
import { WarpCanvas } from "./WarpCanvas.tsx";

export type WarpBackgroundProps = {
  active: boolean;
};

/**
 * Theme-aware warp canvas. Maps primary to trail and secondary to core once here.
 */
export function WarpBackground({ active }: WarpBackgroundProps) {
  const tokens = useThemeTokens();
  return (
    <WarpCanvas
      active={active}
      trailColor={tokens.primary}
      coreColor={tokens.secondary}
    />
  );
}
