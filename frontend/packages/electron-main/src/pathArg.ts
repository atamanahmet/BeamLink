import path from "path";

/**
 * Returns true when the value is a non-empty absolute path string.
 */
export function isAbsolutePathArg(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && path.isAbsolute(value);
}
