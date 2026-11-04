import { useMemo, useState } from "react";
import { Button, Modal, Stack, Text } from "@mantine/core";
import { useMantineColorScheme } from "@mantine/core";
import { SettingsAppearanceSection } from "@beamlink/features";
import { loadThemes, useThemeSettings } from "@beamlink/ui";
import { NexusSettingsSection } from "../components/NexusSettingsSection.tsx";
import { Beamlink } from "../native/beamlinkPlugin.ts";

export function Component() {
  const { themeId, setThemeId } = useThemeSettings();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const themes = useMemo(
    () => loadThemes().map((theme) => ({ id: theme.id, name: theme.name })),
    [],
  );
  const [confirmReset, setConfirmReset] = useState(false);
  const [resetBusy, setResetBusy] = useState(false);

  /**
   * Local reset then reload so the UI matches a fresh install.
   */
  const resetDevice = async () => {
    setResetBusy(true);
    try {
      await Beamlink.resetDevice();
      window.location.reload();
    } catch {
      setResetBusy(false);
    }
  };

  return (
    <Stack gap="lg" w="100%">
      <NexusSettingsSection />
      <Stack gap="sm">
        <Text fw={600} size="md" c="var(--bl-text-primary)">
          Appearance
        </Text>
        <SettingsAppearanceSection
          themes={themes}
          themeId={themeId}
          onThemeIdChange={setThemeId}
          colorScheme={colorScheme}
          onColorSchemeChange={setColorScheme}
        />
      </Stack>
      <Stack gap="sm">
        <Button color="danger" onClick={() => setConfirmReset(true)}>
          Reset device
        </Button>
      </Stack>
      <Modal
        opened={confirmReset}
        onClose={() => {
          if (resetBusy) return;
          setConfirmReset(false);
        }}
        title="Reset this device?"
      >
        <Stack gap="md">
          <Text size="sm" c="var(--bl-text-secondary)">
            Remove the old entry on the nexus.
          </Text>
          <Button
            fullWidth
            color="danger"
            loading={resetBusy}
            onClick={() => void resetDevice()}
          >
            Reset device
          </Button>
          <Button
            variant="default"
            disabled={resetBusy}
            onClick={() => setConfirmReset(false)}
          >
            Keep
          </Button>
        </Stack>
      </Modal>
    </Stack>
  );
}
