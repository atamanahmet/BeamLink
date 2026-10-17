import { useQuery } from "@tanstack/react-query";
import type { PeersApi } from "@beamlink/core";

type PeersQueryOptions = {
  enabled: boolean;
};

/**
 * Shared peers list for TransferView and FileUpload.
 */
export function usePeersQuery(api: PeersApi, options: PeersQueryOptions) {
  return useQuery({
    queryKey: ["peers"],
    queryFn: () => api.getPeers(),
    refetchInterval: 5000,
    enabled: options.enabled,
  });
}
