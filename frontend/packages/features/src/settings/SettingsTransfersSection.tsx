import { Stack } from "@mantine/core";
import type { SpeedUnit } from "../transfers/speedUnits.ts";
import { AdvancedMinFreeSpaceSection } from "./AdvancedMinFreeSpaceSection.tsx";
import { ResumePolicySection } from "./ResumePolicySection.tsx";
import { SpeedCapSection } from "./SpeedCapSection.tsx";
import { UnitSelect } from "./UnitSelect.tsx";
import type { MinFreeSpaceState, SpeedCapState } from "./types.ts";
import type { ResumePolicyControls } from "./useResumePolicySettings.ts";

export type SettingsTransfersSectionProps = {
  speedCap: SpeedCapState;
  onChangeSpeedCapValue: (value: string) => void;
  onSaveSpeedCap: () => void;
  unit: SpeedUnit;
  onChangeUnit: (unit: SpeedUnit) => void;
  resumePolicy?: ResumePolicyControls;
  minFreeSpace: MinFreeSpaceState;
  onChangeMinFreeSpaceValue: (value: string) => void;
  onSaveMinFreeSpace: () => void;
  onResetMinFreeSpace: () => void;
};

/**
 * Transfer settings: speed cap, unit, optional resume policy.
 */
export function SettingsTransfersSection({
  speedCap,
  onChangeSpeedCapValue,
  onSaveSpeedCap,
  unit,
  onChangeUnit,
  resumePolicy,
  minFreeSpace,
  onChangeMinFreeSpaceValue,
  onSaveMinFreeSpace,
  onResetMinFreeSpace,
}: SettingsTransfersSectionProps) {
  return (
    <Stack gap="md">
      <SpeedCapSection
        speedCap={speedCap}
        onChangeValue={onChangeSpeedCapValue}
        onSave={onSaveSpeedCap}
      />
      <UnitSelect value={unit} onChange={onChangeUnit} />
      {resumePolicy ? (
        <ResumePolicySection
          resumePolicy={resumePolicy.value}
          onChangeValue={resumePolicy.onChangeValue}
          onSave={resumePolicy.onSave}
        />
      ) : null}
      <AdvancedMinFreeSpaceSection
        minFreeSpace={minFreeSpace}
        onChangeValue={onChangeMinFreeSpaceValue}
        onSave={onSaveMinFreeSpace}
        onResetDefault={onResetMinFreeSpace}
      />
    </Stack>
  );
}
