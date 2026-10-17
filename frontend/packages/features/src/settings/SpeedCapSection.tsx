import {
  Button,
  Group,
  Loader,
  NumberInput,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { Gauge } from "lucide-react";
import type { SpeedCapState } from "./types.ts";
import { StatusMessage } from "./StatusMessage.tsx";

type SpeedCapSectionProps = {
  speedCap: SpeedCapState;
  onChangeValue: (value: string) => void;
  onSave: () => void;
};

/**
 * Transfer speed cap number field and save action.
 */
export function SpeedCapSection({
  speedCap,
  onChangeValue,
  onSave,
}: SpeedCapSectionProps) {
  const numberValue =
    speedCap.value === "" ? "" : Number(speedCap.value);

  return (
    <Paper
      withBorder
      p="lg"
      radius="md"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-border)",
      }}
    >
      <Stack gap="md">
        <Group gap="xs">
          <Gauge size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Transfer Speed Cap
          </Text>
        </Group>

        <Text size="xs" c="var(--bl-text-muted)">
          Max outbound transfer speed. Default is 80 Mbps. Set higher only if
          you want to saturate your network link.
        </Text>

        <Group gap="xs">
          <NumberInput
            min={1}
            max={1000}
            value={numberValue}
            onChange={(val) => {
              if (val === "" || val === undefined || val === null) {
                onChangeValue("");
                return;
              }
              onChangeValue(String(val));
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") onSave();
            }}
            w={128}
            styles={{ input: { fontFamily: "monospace" } }}
          />
          <Text size="sm" c="var(--bl-text-secondary)">
            Mbps
          </Text>
          <Button
            onClick={onSave}
            disabled={speedCap.save === "saving"}
            leftSection={
              speedCap.save === "saving" ? (
                <Loader size={16} color="white" type="dots" />
              ) : undefined
            }
          >
            {speedCap.save === "saving" ? "Saving..." : "Save"}
          </Button>
        </Group>

        {speedCap.save === "success" ? (
          <StatusMessage type="success" message="Speed cap updated." />
        ) : null}
        {speedCap.error ? (
          <StatusMessage type="error" message={speedCap.error} />
        ) : null}
      </Stack>
    </Paper>
  );
}
