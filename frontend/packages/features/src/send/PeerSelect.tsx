import { NativeSelect, Stack, Text } from "@mantine/core";

export type PeerOption = {
  agentId: string;
  agentName: string;
  ipAddress: string;
  port: number;
  online: boolean;
};

export type PeerSelectProps = {
  options: PeerOption[];
  value: string;
  onChange: (agentId: string) => void;
  disabled: boolean;
  isOffline: boolean;
};

export const PeerSelect = ({
  options,
  value,
  onChange,
  disabled,
  isOffline,
}: PeerSelectProps) => (
  <Stack gap="xs">
    <NativeSelect
      label="Destination Agent"
      value={value}
      onChange={(e) => onChange(e.currentTarget.value)}
      disabled={disabled}
      data={[
        { value: "", label: "Choose destination agent..." },
        ...options.map((peer) => ({
          value: peer.agentId,
          label: `${peer.agentName} (${peer.ipAddress}:${peer.port}) — ${peer.online ? "Online" : "Offline"}`,
        })),
      ]}
    />
    {options.length === 0 ? (
      <Text size="sm" c="var(--bl-text-secondary)">
        No agents registered.
      </Text>
    ) : null}
    {isOffline ? (
      <Text size="sm" c="var(--bl-danger-text)">
        This agent is offline.
      </Text>
    ) : null}
  </Stack>
);
