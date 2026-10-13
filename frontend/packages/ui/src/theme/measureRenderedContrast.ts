import { contrastRatio } from "./contrast.ts";

export type RenderedContrast = {
  name: string;
  ratio: number;
  minimum: number;
  pass: boolean;
  foreground: string;
  background: string;
};

/**
 * Builds a contrast result from two CSS color strings.
 */
export function measureContrastPair(
  name: string,
  foreground: string,
  background: string,
  minimum: number,
): RenderedContrast {
  const ratio = contrastRatio(foreground, background);
  return {
    name,
    ratio,
    minimum,
    pass: ratio >= minimum,
    foreground,
    background,
  };
}

/**
 * Returns true when a CSS color is fully transparent.
 */
function isTransparent(color: string): boolean {
  const value = color.trim().toLowerCase();
  return (
    value === "transparent" ||
    value === "rgba(0, 0, 0, 0)" ||
    value === "rgba(0,0,0,0)"
  );
}

/**
 * Walks ancestors to find the first opaque background color.
 */
function effectiveBackground(el: Element): string {
  let node: Element | null = el;
  while (node) {
    const bg = getComputedStyle(node).backgroundColor;
    if (!isTransparent(bg)) {
      return bg;
    }
    node = node.parentElement;
  }
  return getComputedStyle(document.body).backgroundColor;
}

/**
 * Measures text color against the effective background for one element.
 */
export function measureRendered(
  testId: string,
  name: string,
  minimum = 4.5,
): RenderedContrast | null {
  const el = document.querySelector(`[data-testid="${testId}"]`);
  if (!el) {
    return null;
  }
  const style = getComputedStyle(el);
  return measureContrastPair(
    name,
    style.color,
    effectiveBackground(el),
    minimum,
  );
}

/**
 * Measures border color against the effective background for one element.
 */
export function measureRenderedBorder(
  testId: string,
  name: string,
  minimum = 3,
): RenderedContrast | null {
  const el = document.querySelector(`[data-testid="${testId}"]`);
  if (!el) {
    return null;
  }
  const style = getComputedStyle(el);
  return measureContrastPair(
    name,
    style.borderTopColor,
    effectiveBackground(el),
    minimum,
  );
}
