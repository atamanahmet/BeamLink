import { describe, expect, test } from "vitest";
import { contrastRatio } from "../theme/contrast.ts";
import { generateTokens } from "../theme/generate.ts";
import { loadThemes } from "../theme/loadThemes.ts";
import type { ThemeMode } from "../theme/types.ts";
import { blendGlassOverWarp } from "./blendGlassOverWarp.ts";

const themes = loadThemes();
const modes: ThemeMode[] = ["light", "dark"];

describe("glass over warp core contrast", () => {
  test.each(
    themes.flatMap((theme) =>
      modes.map((mode) => ({ themeId: theme.id, mode, seeds: theme.seeds })),
    ),
  )(
    "$themeId $mode text-primary on glass over secondary is at least 4.5",
    ({ themeId, mode, seeds }) => {
      const tokens = generateTokens(seeds, mode);
      const blended = blendGlassOverWarp(tokens.glass, tokens.secondary);
      const ratio = contrastRatio(tokens["text-primary"], blended);
      expect(
        ratio,
        `${themeId} ${mode} ratio=${ratio.toFixed(3)} blend=${blended}`,
      ).toBeGreaterThanOrEqual(4.5);
    },
  );
});
