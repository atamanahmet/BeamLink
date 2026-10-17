import { ThemeSelect, type ThemeMode, type ThemeSelectOption } from "./ThemeSelect.tsx";

export type SettingsAppearanceSectionProps = {
  themes: ThemeSelectOption[];
  themeId: string;
  onThemeIdChange: (id: string) => void;
  colorScheme: ThemeMode;
  onColorSchemeChange: (mode: ThemeMode) => void;
};

/**
 * Appearance settings: theme and light or dark mode.
 */
export function SettingsAppearanceSection({
  themes,
  themeId,
  onThemeIdChange,
  colorScheme,
  onColorSchemeChange,
}: SettingsAppearanceSectionProps) {
  return (
    <ThemeSelect
      themes={themes}
      value={themeId}
      onChange={onThemeIdChange}
      mode={colorScheme}
      onModeChange={onColorSchemeChange}
    />
  );
}
