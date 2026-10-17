import { useState } from "react";
import { Button, Group, NumberInput, Text } from "@mantine/core";
import type { SpeedUnit } from "./speedUnits.ts";
import { fromMbps, parseSpeedInput } from "./speedUnits.ts";

export type TransferSpeedLimitFieldProps = {
  unit: SpeedUnit;
  speedLimitMbps: number | null;
  effectiveLimitMbps: number | null;
  disabled?: boolean;
  onSave: (limitMbps: number | null) => void;
};

/**
 * Per-transfer speed limit input with set and clear.
 */
export function TransferSpeedLimitField({
  unit,
  speedLimitMbps,
  effectiveLimitMbps,
  disabled = false,
  onSave,
}: TransferSpeedLimitFieldProps) {
  const [draft, setDraft] = useState<string | number>(
    speedLimitMbps == null ? "" : fromMbps(speedLimitMbps, unit),
  );

  return (
    <Group gap="xs" wrap="wrap" align="center">
      <NumberInput
        size="xs"
        w={96}
        min={0}
        value={draft}
        disabled={disabled}
        onChange={(val) => {
          if (val === "" || val === undefined || val === null) {
            setDraft("");
            return;
          }
          setDraft(val);
        }}
        styles={{ input: { fontFamily: "monospace" } }}
        aria-label="Speed limit"
      />
      <Text size="xs" c="var(--bl-text-secondary)">
        {unit}
      </Text>
      <Button
        size="xs"
        variant="light"
        color="brand"
        disabled={disabled}
        onClick={() => {
          const raw = draft === "" ? "" : String(draft);
          const parsed = parseSpeedInput(raw, unit);
          if (parsed !== null && Number.isNaN(parsed)) return;
          onSave(parsed);
        }}
      >
        Set
      </Button>
      <Button
        size="xs"
        variant="subtle"
        color="brand"
        disabled={disabled}
        onClick={() => {
          setDraft("");
          onSave(null);
        }}
      >
        Clear
      </Button>
      {effectiveLimitMbps != null ? (
        <Text size="xs" c="var(--bl-text-muted)">
          Effective {fromMbps(effectiveLimitMbps, unit).toFixed(2)} {unit}
        </Text>
      ) : (
        <Text size="xs" c="var(--bl-text-muted)">
          Effective unlimited
        </Text>
      )}
    </Group>
  );
}
