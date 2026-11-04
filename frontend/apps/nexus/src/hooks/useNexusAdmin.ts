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
 * Approved devices every 3s.
 */
export function useNexusDevicesQuery(api: NexusAdminApi = nexusAdminApi) {
  return useQuery({
    queryKey: ["nexus", "devices", "approved"],
    queryFn: () => api.getApprovedDevices(),
    refetchInterval: 3000,
  });
}

/**
 * Pending devices and renames.
 */
export function useNexusPendingQuery(api: NexusAdminApi = nexusAdminApi) {
  return useQuery({
    queryKey: ["nexus", "devices", "pending"],
    queryFn: async () => {
      const [devices, renames] = await Promise.all([
        api.getPendingDevices(),
        api.getPendingRenames(),
      ]);
      return { devices, renames };
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
 * Admin audit log page. No polling.
 */
export function useNexusAuditLogQuery(
  page: number,
  size = 50,
  api: NexusAdminApi = nexusAdminApi,
) {
  return useQuery({
    queryKey: ["nexus", "audit-log", page, size],
    queryFn: () => api.getAuditLog(page, size),
  });
}

/**
 * Approve / reject / remove mutations for nexus admin.
 */
export function useNexusAdminMutations(api: NexusAdminApi = nexusAdminApi) {
  const queryClient = useQueryClient();

  const invalidateDevices = () => {
    void queryClient.invalidateQueries({ queryKey: ["nexus", "devices"] });
    void queryClient.invalidateQueries({ queryKey: ["nexus", "stats"] });
  };

  const approveDevice = useMutation({
    mutationFn: (id: string) => api.approveDevice(id),
    onSuccess: invalidateDevices,
  });

  const rejectDevice = useMutation({
    mutationFn: (id: string) => api.rejectDevice(id),
    onSuccess: invalidateDevices,
  });

  const removeDevice = useMutation({
    mutationFn: (id: string) => api.removeDevice(id),
    onSuccess: invalidateDevices,
  });

  const approveRename = useMutation({
    mutationFn: (id: string) => api.approveRename(id),
    onSuccess: invalidateDevices,
  });

  const rejectRename = useMutation({
    mutationFn: (id: string) => api.rejectRename(id),
    onSuccess: invalidateDevices,
  });

  return {
    approveDevice,
    rejectDevice,
    removeDevice,
    approveRename,
    rejectRename,
  };
}
