export const GIB_IN_BYTES = 1073741824;

/**
 * Converts GiB to whole bytes. 1 GiB is 1073741824 bytes.
 */
export function gibToBytes(gib: number): number {
  return Math.round(gib * GIB_IN_BYTES);
}

/**
 * Converts bytes to GiB.
 */
export function bytesToGib(bytes: number): number {
  return bytes / GIB_IN_BYTES;
}
