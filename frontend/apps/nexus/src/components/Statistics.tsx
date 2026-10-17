import { Users, Wifi, WifiOff, Clock, Activity, HardDrive } from "lucide-react";
import { Paper, SimpleGrid, Stack, Text } from "@mantine/core";
import { formatBytes } from "@beamlink/features";

interface StatisticsProps {
  stats: {
    agentStats: {
      total: number;
      online: number;
      offline: number;
      pending: number;
      pendingRename: number;
    };
    transferStats: {
      totalTransfers: number;
      totalDataTransferred: number;
    };
  };
}

export const Statistics = ({ stats }: StatisticsProps) => {
  const statCards = [
    {
      icon: Users,
      label: "Total Agents",
      value: stats.agentStats.total,
      color: "orange",
    },
    {
      icon: Wifi,
      label: "Online",
      value: stats.agentStats.online,
      color: "green",
    },
    {
      icon: WifiOff,
      label: "Offline",
      value: stats.agentStats.offline,
      color: "red",
    },
    {
      icon: Clock,
      label: "Pending",
      value: stats.agentStats.pending,
      color: "red",
    },
    {
      icon: Activity,
      label: "Transfers",
      value: stats.transferStats.totalTransfers,
      color: "green",
    },
    {
      icon: HardDrive,
      label: "Data Transferred",
      value: formatBytes(stats.transferStats.totalDataTransferred),
      color: "orange",
    },
  ];

  return (
    <SimpleGrid cols={{ base: 1, sm: 2, md: 3, xl: 6 }} spacing="md" mb="xl">
      {statCards.map((stat, index) => {
        const Icon = stat.icon;
        return (
          <Paper key={index} withBorder p="md" radius="md">
            <Stack gap="xs">
              <Icon size={20} color="var(--bl-primary)" />
              <Text size="xs" c="var(--bl-text-secondary)">
                {stat.label}
              </Text>
              <Text size="xl" fw={700} c="var(--bl-primary-text)">
                {stat.value || 0}
              </Text>
            </Stack>
          </Paper>
        );
      })}
    </SimpleGrid>
  );
};
