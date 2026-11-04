/**
 * Peer id for a transfer row: source when local is target, target when local is source.
 */
export function resolveTransferPeerId(
  localDeviceId: string | null | undefined,
  sourceDeviceId: string | null | undefined,
  targetDeviceId: string | null | undefined,
): string | null {
  const local = localDeviceId?.trim() || null;
  const source = sourceDeviceId?.trim() || null;
  const target = targetDeviceId?.trim() || null;

  if (local != null && local === target) {
    return source;
  }
  if (local != null && local === source) {
    return target;
  }
  if (target == null && source != null) {
    return source;
  }
  if (source == null && target != null) {
    return target;
  }
  return target ?? source;
}
