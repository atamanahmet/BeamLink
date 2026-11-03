import {
  Button,
  Group,
  Loader,
  NumberInput,
  Paper,
  Stack,
  Text,
} from "@mantine/core";
import { HardDrive } from "lucide-react";
import { bytesToGib } from "@beamlink/core";
import type { MinFreeSpaceState } from "./types.ts";
import { StatusMessage } from "./StatusMessage.tsx";

const DEFAULT_BYTES = 1073741824;

type AdvancedMinFreeSpaceSectionProps = {
  minFreeSpace: MinFreeSpaceState;
  onChangeValue: (value: string) => void;
  onSave: () => void;
  onResetDefault: () => void;
};

/**
 * Advanced disk reserve field in GiB.
 */
export function AdvancedMinFreeSpaceSection({
  minFreeSpace,
  onChangeValue,
  onSave,
  onResetDefault,
}: AdvancedMinFreeSpaceSectionProps) {
  const numberValue =
    minFreeSpace.value === "" ? "" : Number(minFreeSpace.value);

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
        <Text
          size="sm"
          fw={600}
          tt="uppercase"
          c="var(--bl-primary-text)"
          style={{ letterSpacing: "0.08em" }}
        >
          Advanced
        </Text>
        <Group gap="xs">
          <HardDrive size={16} color="var(--bl-primary-text)" />
          <Text size="sm" fw={600} c="var(--bl-primary-text)">
            Disk reserve
          </Text>
        </Group>
        <Text size="xs" c="var(--bl-text-muted)">
          Bytes a transfer must leave free. Default is{" "}
          {bytesToGib(DEFAULT_BYTES)} GiB.
        </Text>
        <Group gap="xs">
          <NumberInput
            min={0.25}
            max={1024}
            step={0.25}
            decimalScale={2}
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
            GiB
          </Text>
          <Button
            onClick={onSave}
            disabled={minFreeSpace.save === "saving"}
            leftSection={
              minFreeSpace.save === "saving" ? (
                <Loader size={16} color="white" type="dots" />
              ) : undefined
            }
          >
            {minFreeSpace.save === "saving" ? "Saving..." : "Save"}
          </Button>
          <Button variant="default" onClick={onResetDefault}>
            Reset to default
          </Button>
        </Group>
        {minFreeSpace.save === "success" ? (
          <StatusMessage type="success" message="Disk reserve updated." />
        ) : null}
        {minFreeSpace.error ? (
          <StatusMessage
            type="error"
            message={minFreeSpace.error}
            correlationId={minFreeSpace.correlationId}
          />
        ) : null}
      </Stack>
    </Paper>
  );
}
