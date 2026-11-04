import { useState } from "react";
import { Activity, Users, Clock } from "lucide-react";
import { Badge, Paper, Stack, Tabs, Text, Title } from "@mantine/core";
import { usePlatform } from "@beamlink/platform";
import { DeviceList } from "./DeviceList";
import { PendingApprovals } from "./PendingApprovals";
import { Statistics } from "./Statistics";
import { TransferLogs } from "./TransferLogs";
import { useNexusStatsQuery } from "../hooks/useNexusAdmin";

export const NetworkView = () => {
  const { capabilities } = usePlatform();
  const { data: stats } = useNexusStatsQuery();

  const [activeTab, setActiveTab] = useState<"devices" | "pending" | "logs">(
    "devices",
  );

  const resolvedStats = stats ?? {
    deviceStats: {
      total: 0,
      online: 0,
      offline: 0,
      pending: 0,
      pendingRename: 0,
    },
    transferStats: {
      totalTransfers: 0,
      totalDataTransferred: 0,
    },
  };

  /**
   * Maps Mantine tab value (string or null) onto activeTab.
   */
  const handleTabChange = (value: string | null) => {
    if (value === "devices" || value === "pending" || value === "logs") {
      setActiveTab(value);
    }
  };

  const pendingCount =
    resolvedStats.deviceStats.pending + resolvedStats.deviceStats.pendingRename;

  return (
    <Stack gap="xl" maw={1280} mx="auto" w="100%">
      <Stack gap={4}>
        <Title order={1} c="var(--bl-primary-text)">
          Nexus
        </Title>
        <Text c="var(--bl-text-secondary)">Central Command & Control</Text>
      </Stack>

      <Statistics stats={resolvedStats} />

      <Paper
        withBorder
        radius="lg"
        style={{
          backgroundColor: "var(--bl-surface)",
          borderColor: "var(--bl-border)",
          overflow: "hidden",
        }}
      >
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tabs.List grow>
            <Tabs.Tab
              value="devices"
              leftSection={<Users size={20} />}
              c="var(--bl-primary-text)"
            >
              Devices
            </Tabs.Tab>

            <Tabs.Tab
              value="pending"
              leftSection={<Clock size={20} />}
              c="var(--bl-primary-text)"
              rightSection={
                pendingCount > 0 ? (
                  <Badge
                    size="sm"
                    circle
                    style={{
                      backgroundColor: "var(--bl-primary)",
                      color: "var(--bl-primary-foreground)",
                    }}
                  >
                    {pendingCount}
                  </Badge>
                ) : undefined
              }
            >
              Pending Approvals
            </Tabs.Tab>

            {capabilities.transferLogs ? (
              <Tabs.Tab
                value="logs"
                leftSection={<Activity size={20} />}
                c="var(--bl-primary-text)"
              >
                Transfer Logs
              </Tabs.Tab>
            ) : null}
          </Tabs.List>
        </Tabs>

        <Stack p="md" gap="md">
          {activeTab === "devices" && <DeviceList />}
          {activeTab === "pending" && <PendingApprovals />}
          {activeTab === "logs" && capabilities.transferLogs ? (
            <TransferLogs />
          ) : null}
        </Stack>
      </Paper>
    </Stack>
  );
};
