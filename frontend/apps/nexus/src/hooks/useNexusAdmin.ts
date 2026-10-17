import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  nexusAdminApi,
  type NexusAdminApi,
} from "../api/nexusAdminApi";

/**
 * Dashboard stats every 5s.
 */
export function useNexusStatsQuery(api: NexusAdminApi = nexusAdminApi) {
  return useQuery({
    queryKey: ["nexus", "stats"],
    queryFn: () => api.getStats(),
    refetchInterval: 5000,
  });
}

/**
 * Approved agents every 3s.
 */
export function useNexusAgentsQuery(api: NexusAdminApi = nexusAdminApi) {
  return useQuery({
    queryKey: ["nexus", "agents", "approved"],
    queryFn: () => api.getApprovedAgents(),
    refetchInterval: 3000,
  });
}

/**
 * Pending agents and renames.
 */
export function useNexusPendingQuery(api: NexusAdminApi = nexusAdminApi) {
  return useQuery({
    queryKey: ["nexus", "agents", "pending"],
    queryFn: async () => {
      const [agents, renames] = await Promise.all([
        api.getPendingAgents(),
        api.getPendingRenames(),
      ]);
      return { agents, renames };
    },
  });
}

/**
 * Admin transfer logs.
 */
export function useNexusTransferLogsQuery(api: NexusAdminApi = nexusAdminApi) {
  return useQuery({
    queryKey: ["nexus", "transfer-logs"],
    queryFn: () => api.getTransferLogs(),
  });
}

/**
 * Approve / reject / remove mutations for nexus admin.
 */
export function useNexusAdminMutations(api: NexusAdminApi = nexusAdminApi) {
  const queryClient = useQueryClient();

  const invalidateAgents = () => {
    void queryClient.invalidateQueries({ queryKey: ["nexus", "agents"] });
    void queryClient.invalidateQueries({ queryKey: ["nexus", "stats"] });
  };

  const approveAgent = useMutation({
    mutationFn: (id: string) => api.approveAgent(id),
    onSuccess: invalidateAgents,
  });

  const rejectAgent = useMutation({
    mutationFn: (id: string) => api.rejectAgent(id),
    onSuccess: invalidateAgents,
  });

  const removeAgent = useMutation({
    mutationFn: (id: string) => api.removeAgent(id),
    onSuccess: invalidateAgents,
  });

  const approveRename = useMutation({
    mutationFn: (id: string) => api.approveRename(id),
    onSuccess: invalidateAgents,
  });

  const rejectRename = useMutation({
    mutationFn: (id: string) => api.rejectRename(id),
    onSuccess: invalidateAgents,
  });

  return {
    approveAgent,
    rejectAgent,
    removeAgent,
    approveRename,
    rejectRename,
  };
}
