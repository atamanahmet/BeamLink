import { useQuery } from "@tanstack/react-query";
import type { HealthApi } from "@beamlink/core";

/**
 * Settings health probe every 15s.
 */
export function useHealthQuery(api: HealthApi) {
  return useQuery({
    queryKey: ["health"],
    queryFn: async () => {
      await api.checkHealth();
      return true;
    },
    refetchInterval: 15000,
  });
}
