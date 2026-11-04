import { NativeSelect, Stack, Text } from "@mantine/core";

export type PeerOption = {
  deviceId: string;
  deviceName: string;
  ipAddress: string;
  port: number;
  online: boolean;
};

export type PeerSelectProps = {
  options: PeerOption[];
  value: string;
  onChange: (deviceId: string) => void;
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
      label="Destination peer"
      value={value}
      onChange={(e) => onChange(e.currentTarget.value)}
      disabled={disabled}
      data={[
        { value: "", label: "Choose destination peer..." },
        ...options.map((peer) => ({
          value: peer.deviceId,
          label: `${peer.deviceName} (${peer.ipAddress}:${peer.port}), ${peer.online ? "Online" : "Offline"}`,
        })),
      ]}
    />
    {options.length === 0 ? (
      <Text size="sm" c="var(--bl-text-secondary)">
        No peers registered.
      </Text>
    ) : null}
    {isOffline ? (
      <Text size="sm" c="var(--bl-danger-text)">
        Peer is offline or has receive turned off
      </Text>
    ) : null}
  </Stack>
);
