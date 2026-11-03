import { Select, SegmentedControl, Stack } from "@mantine/core";

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
    <Stack gap="md">
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
        color="brand"
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
  );
}
