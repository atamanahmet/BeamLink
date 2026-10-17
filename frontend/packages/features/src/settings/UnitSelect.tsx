import { Paper, Select, Stack, Text, Group } from "@mantine/core";
import { Gauge } from "lucide-react";
import { SPEED_UNITS, type SpeedUnit } from "../transfers/speedUnits.ts";

export type UnitSelectProps = {
  value: SpeedUnit;
  onChange: (unit: SpeedUnit) => void;
};

/**
 * Speed unit preference picker for Settings.
 */
export function UnitSelect({ value, onChange }: UnitSelectProps) {
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
            Speed Unit
          </Text>
        </Group>

        <Select
          label="Display unit"
          value={value}
          onChange={(next) => {
            if (next != null && (SPEED_UNITS as readonly string[]).includes(next)) {
              onChange(next as SpeedUnit);
            }
          }}
          data={SPEED_UNITS.map((unit) => ({
            value: unit,
            label: unit,
          }))}
          allowDeselect={false}
        />
      </Stack>
    </Paper>
  );
}
