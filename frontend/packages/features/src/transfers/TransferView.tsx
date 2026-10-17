import { useMemo, useState } from "react";
import { Stack } from "@mantine/core";
import type {
  PeersApi,
  SettingsApi,
  TransferApi,
  TransferSummary,
} from "@beamlink/core";
import { TransferListBody } from "./TransferListBody.tsx";
import { TransfersEmpty } from "./TransfersEmpty.tsx";
import { TransfersHeader } from "./TransfersHeader.tsx";
import { countByState, filterTransfers } from "./transferFilter.ts";
import { showTransferActionError } from "./showTransferActionError.ts";
import { transferActionErrorMessage } from "./transferErrors.ts";
import { groupTransfers } from "./transferGrouping.ts";
import { useTransferHeaderState } from "./useTransferHeaderState.ts";
import { useUnitPreference } from "./useUnitPreference.ts";
import { usePeersQuery } from "../hooks/usePeersQuery.ts";
import {
  useTransferActions,
  useTransfersQuery,
} from "../hooks/useTransfersQuery.ts";

export type TransferViewProps = {
  transferApi: TransferApi;
  peersApi: PeersApi;
  settingsApi: SettingsApi;
  enabled: boolean;
  localAgentId?: string | null;
};

/**
 * Transfer list container. Apis are injected by the app route.
 */
export function TransferView({
  transferApi,
  peersApi,
  settingsApi,
  enabled,
  localAgentId = null,
}: TransferViewProps) {
  const { data: transfers = [], refetch } = useTransfersQuery(transferApi, {
    enabled,
  });
  const { data: peers = [] } = usePeersQuery(peersApi, { enabled });
  const { pause, resume, cancel, remove, setSpeedLimit } =
    useTransferActions(transferApi);
  const { unit } = useUnitPreference();
  const header = useTransferHeaderState(settingsApi, enabled, unit);

  const [actionLoading, setActionLoading] = useState<Record<string, boolean>>(
    {},
  );
  const [inlineErrors, setInlineErrors] = useState<Record<string, string>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [childrenMap, setChildrenMap] = useState<
    Record<string, TransferSummary[]>
  >({});
  const [groupExpanded, setGroupExpanded] = useState<Record<string, boolean>>(
    {},
  );

  const filtered = useMemo(
    () => filterTransfers(transfers, header.filter),
    [transfers, header.filter],
  );
  const counts = useMemo(() => countByState(transfers), [transfers]);
  const { groups, standalones } = useMemo(
    () => groupTransfers(filtered),
    [filtered],
  );

  const items = useMemo(() => {
    const list = [
      ...standalones.map((t) => ({
        kind: "standalone" as const,
        t,
        createdAt: new Date(t.createdAt).getTime(),
      })),
      ...groups.map((g) => ({
        kind: "group" as const,
        g,
        createdAt: new Date(g.createdAt).getTime(),
      })),
    ];
    return list.sort((a, b) => b.createdAt - a.createdAt);
  }, [standalones, groups]);

  const setLoading = (id: string, val: boolean) =>
    setActionLoading((prev) => ({ ...prev, [id]: val }));

  const setError = (id: string, msg: string) =>
    setInlineErrors((prev) => ({ ...prev, [id]: msg }));

  const toggleExpand = async (t: TransferSummary) => {
    if (t.type === "SINGLE") return;
    const isOpen = expanded[t.id];
    if (!isOpen && !childrenMap[t.id]) {
      try {
        const data = await transferApi.getChildTransfers(t.id, t.type);
        setChildrenMap((prev) => ({ ...prev, [t.id]: data }));
      } catch {
        setChildrenMap((prev) => ({ ...prev, [t.id]: [] }));
      }
    }
    setExpanded((prev) => ({ ...prev, [t.id]: !isOpen }));
  };

  const runAction = async (
    t: TransferSummary,
    kind: "pause" | "resume" | "cancel" | "delete",
  ) => {
    setLoading(t.id, true);
    setError(t.id, "");
    try {
      if (kind === "pause") await pause.mutateAsync({ id: t.id, type: t.type });
      else if (kind === "resume")
        await resume.mutateAsync({ id: t.id, type: t.type });
      else if (kind === "cancel")
        await cancel.mutateAsync({ id: t.id, type: t.type });
      else await remove.mutateAsync({ id: t.id, type: t.type });
    } catch (err: unknown) {
      const fallback =
        kind === "pause"
          ? "Pause failed"
          : kind === "resume"
            ? "Resume failed"
            : kind === "cancel"
              ? "Cancel failed"
              : "Delete failed";
      showTransferActionError(err, fallback);
    } finally {
      setLoading(t.id, false);
    }
  };

  const runSpeedLimit = async (
    t: TransferSummary,
    limitMbps: number | null,
  ) => {
    setLoading(t.id, true);
    setError(t.id, "");
    try {
      await setSpeedLimit.mutateAsync({ id: t.id, type: t.type, limitMbps });
    } catch (err: unknown) {
      setError(t.id, transferActionErrorMessage(err, "Speed limit failed"));
    } finally {
      setLoading(t.id, false);
    }
  };

  return (
    <Stack gap="md" w="100%" maw={1100} mx="auto">
      <TransfersHeader
        unit={unit}
        totalLimitMbps={header.totalLimitMbps}
        totalLimitDraft={header.totalLimitDraft}
        onTotalLimitDraftChange={header.setTotalLimitDraft}
        onSaveTotalLimit={() => void header.saveTotalLimit()}
        filter={header.filter}
        counts={counts}
        onFilterChange={header.setFilter}
        onOpenFolder={() => void transferApi.openUploadsFolder()}
        onRefresh={() => void refetch()}
      />

      {transfers.length === 0 || filtered.length === 0 ? (
        <TransfersEmpty />
      ) : (
        <TransferListBody
          items={items}
          peers={peers}
          unit={unit}
          localAgentId={localAgentId}
          actionLoading={actionLoading}
          inlineErrors={inlineErrors}
          expanded={expanded}
          childrenMap={childrenMap}
          groupExpanded={groupExpanded}
          onToggleExpand={(t) => void toggleExpand(t)}
          onToggleGroup={(id) =>
            setGroupExpanded((prev) => ({ ...prev, [id]: !prev[id] }))
          }
          onPause={(t) => void runAction(t, "pause")}
          onResume={(t) => void runAction(t, "resume")}
          onCancel={(t) => void runAction(t, "cancel")}
          onDelete={(t) => void runAction(t, "delete")}
          onSetSpeedLimit={(t, limit) => void runSpeedLimit(t, limit)}
        />
      )}
    </Stack>
  );
}
