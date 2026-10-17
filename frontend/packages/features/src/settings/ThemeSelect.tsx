import { Paper, Select, SegmentedControl, Stack, Text, Group } from "@mantine/core";
import { Palette } from "lucide-react";

export type ThemeSelectOption = {
  id: string;
  name: string;
};

export type ThemeMode = "light" | "dark" | "auto";

export type ThemeSelectProps = {
  themes: ThemeSelectOption[];
  value: string;
  onChange: (themeId: string) => void;
  mode: ThemeMode;
  onModeChange: (mode: ThemeMode) => void;
};

/**
 * Theme id and color-scheme mode picker. Storage stays in the container/controller.
 */
export function ThemeSelect({
  themes,
  value,
  onChange,
  mode,
  onModeChange,
}: ThemeSelectProps) {
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
          <Palette size={16} color="var(--bl-primary-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-primary-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Theme
          </Text>
        </Group>

        <SegmentedControl
          value={mode}
          onChange={(next) => {
            if (next === "light" || next === "dark" || next === "auto") {
              onModeChange(next);
            }
          }}
          data={[
            { value: "light", label: "Light" },
            { value: "dark", label: "Dark" },
            { value: "auto", label: "Auto" },
          ]}
          fullWidth
        />

        <Select
          label="Color theme"
          value={value}
          onChange={(next) => {
            if (next != null) onChange(next);
          }}
          data={themes.map((theme) => ({
            value: theme.id,
            label: theme.name,
          }))}
          allowDeselect={false}
        />
      </Stack>
    </Paper>
  );
}
