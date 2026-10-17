/**
 * Peer id for a transfer row: source when local is target, target when local is source.
 */
export function resolveTransferPeerId(
  localAgentId: string | null | undefined,
  sourceAgentId: string | null | undefined,
  targetAgentId: string | null | undefined,
): string | null {
  const local = localAgentId?.trim() || null;
  const source = sourceAgentId?.trim() || null;
  const target = targetAgentId?.trim() || null;

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
