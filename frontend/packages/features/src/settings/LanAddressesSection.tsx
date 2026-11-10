import { useEffect, useState } from "react";
import { ActionIcon, Group, Paper, Stack, Text, Title } from "@mantine/core";
import { Check, Copy } from "lucide-react";
import type { LanAddressesResponse } from "@beamlink/core";

export type LanAddressesSectionProps = {
  load: () => Promise<LanAddressesResponse>;
};

/**
 * Shows LAN TLS ip:port lines with a copy button for each.
 */
export function LanAddressesSection({ load }: LanAddressesSectionProps) {
  const [lines, setLines] = useState<string[]>([]);
  const [copied, setCopied] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    void load()
      .then((res) => {
        if (!alive) return;
        const port = res.tlsPort;
        setLines((res.addresses ?? []).map((ip) => `${ip}:${port}`));
      })
      .catch(() => {
        if (alive) setLines([]);
      });
    return () => {
      alive = false;
    };
  }, [load]);

  if (lines.length === 0) {
    return (
      <Paper withBorder p="md" radius="md">
        <Stack gap="xs">
          <Title order={4}>LAN address</Title>
          <Text size="sm" c="dimmed">
            No private LAN IPv4 found.
          </Text>
        </Stack>
      </Paper>
    );
  }

  return (
    <Paper withBorder p="md" radius="md">
      <Stack gap="xs">
        <Title order={4}>LAN address</Title>
        <Text size="sm" c="dimmed">
          Share one of these with phones and peers on your network.
        </Text>
        {lines.map((line) => (
          <Group key={line} justify="space-between" gap="sm" wrap="nowrap">
            <Text ff="monospace" size="sm">
              {line}
            </Text>
            <ActionIcon
              variant="subtle"
              aria-label={`Copy ${line}`}
              onClick={() => {
                void navigator.clipboard.writeText(line).then(() => {
                  setCopied(line);
                  window.setTimeout(() => setCopied(null), 1200);
                });
              }}
            >
              {copied === line ? <Check size={16} /> : <Copy size={16} />}
            </ActionIcon>
          </Group>
        ))}
      </Stack>
    </Paper>
  );
}
