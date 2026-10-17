import { useSyncExternalStore } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { TransferApi, TransferSummary, TransferType } from "@beamlink/core";
import {
  getTransferPollInterval,
  hasActive,
} from "../transfers/transferPoll.ts";

export { getTransferPollInterval, hasActive };

type TransfersQueryOptions = {
  enabled?: boolean;
};

/**
 * Polls transfers; interval depends on active work.
 */
export function useTransfersQuery(
  api: TransferApi,
  options: TransfersQueryOptions = {},
) {
  const enabled = options.enabled ?? true;

  return useQuery({
    queryKey: ["transfers"],
    queryFn: () => api.getAllTransfers(),
    enabled,
    refetchInterval: (query) => getTransferPollInterval(query.state.data),
  });
}

/**
 * True when the transfers cache has an active row.
 */
export function useHasActiveTransfers(): boolean {
  const queryClient = useQueryClient();

  return useSyncExternalStore(
    (onStoreChange) =>
      queryClient.getQueryCache().subscribe((event) => {
        if (event?.query.queryKey[0] === "transfers") onStoreChange();
      }),
    () =>
      hasActive(
        queryClient.getQueryData<TransferSummary[]>(["transfers"]) ?? [],
      ),
    () => false,
  );
}

type TransferAction = {
  id: string;
  type: TransferType;
};

type SpeedLimitAction = TransferAction & {
  limitMbps: number | null;
};

/**
 * Pause, resume, cancel, delete, speed limit; each invalidates transfers.
 */
export function useTransferActions(api: TransferApi) {
  const queryClient = useQueryClient();

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ["transfers"] });

  const pause = useMutation({
    mutationFn: ({ id, type }: TransferAction) => api.pauseTransfer(id, type),
    onSuccess: invalidate,
  });

  const resume = useMutation({
    mutationFn: ({ id, type }: TransferAction) => api.resumeTransfer(id, type),
    onSuccess: invalidate,
  });

  const cancel = useMutation({
    mutationFn: ({ id, type }: TransferAction) => api.cancelTransfer(id, type),
    onSuccess: invalidate,
  });

  const remove = useMutation({
    mutationFn: ({ id, type }: TransferAction) => api.deleteTransfer(id, type),
    onSuccess: invalidate,
  });

  const setSpeedLimit = useMutation({
    mutationFn: ({ id, type, limitMbps }: SpeedLimitAction) =>
      api.setSpeedLimit(id, type, limitMbps),
    onSuccess: invalidate,
  });

  return { pause, resume, cancel, remove, setSpeedLimit };
}
