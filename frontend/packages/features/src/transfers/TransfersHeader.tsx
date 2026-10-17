import { Button, Chip, Group, NumberInput, Text, Title } from "@mantine/core";
import type { SpeedUnit } from "./speedUnits.ts";
import { fromMbps } from "./speedUnits.ts";
import {
  TRANSFER_STATE_FILTERS,
  type TransferStateFilter,
} from "./transferFilter.ts";

type TransfersHeaderProps = {
  unit: SpeedUnit;
  totalLimitMbps: number | null;
  totalLimitDraft: string | number;
  onTotalLimitDraftChange: (value: string | number) => void;
  onSaveTotalLimit: () => void;
  filter: TransferStateFilter;
  counts: Record<TransferStateFilter, number>;
  onFilterChange: (filter: TransferStateFilter) => void;
  onOpenFolder: () => void;
  onRefresh: () => void;
};

/**
 * Transfers page title, total limit, filters, and header actions.
 */
export function TransfersHeader({
  unit,
  totalLimitMbps,
  totalLimitDraft,
  onTotalLimitDraftChange,
  onSaveTotalLimit,
  filter,
  counts,
  onFilterChange,
  onOpenFolder,
  onRefresh,
}: TransfersHeaderProps) {
  return (
    <>
      <Group justify="space-between" mb="sm" wrap="wrap">
        <Title order={2} c="var(--bl-text-primary)">
          Transfers
        </Title>
        <Group gap="sm">
          <Button variant="light" color="brand" onClick={onOpenFolder}>
            Open Directory
          </Button>
          <Button variant="light" color="brand" onClick={onRefresh}>
            Refresh
          </Button>
        </Group>
      </Group>

      <Group gap="sm" mb="sm" wrap="wrap" align="center">
        <Text size="sm" c="var(--bl-text-secondary)">
          Total limit
        </Text>
        <NumberInput
          size="xs"
          w={96}
          min={0}
          value={totalLimitDraft}
          onChange={(val) => {
            if (val === "" || val === undefined || val === null) {
              onTotalLimitDraftChange("");
              return;
            }
            onTotalLimitDraftChange(val);
          }}
          styles={{ input: { fontFamily: "monospace" } }}
          aria-label="Total speed limit"
        />
        <Text size="xs" c="var(--bl-text-secondary)">
          {unit}
        </Text>
        <Button size="xs" variant="light" color="brand" onClick={onSaveTotalLimit}>
          Save
        </Button>
        <Text size="xs" c="var(--bl-text-muted)">
          {totalLimitMbps == null
            ? "Unlimited"
            : `${fromMbps(totalLimitMbps, unit).toFixed(2)} ${unit}`}
        </Text>
      </Group>

      <Chip.Group
        value={filter}
        onChange={(value) => {
          onFilterChange(value as TransferStateFilter);
        }}
      >
        <Group gap="xs" mb="md" wrap="wrap">
          {TRANSFER_STATE_FILTERS.map((key) => (
            <Chip key={key} value={key} variant="light" color="brand">
              {key === "ALL"
                ? "All"
                : key.charAt(0) + key.slice(1).toLowerCase()}{" "}
              ({counts[key]})
            </Chip>
          ))}
        </Group>
      </Chip.Group>
    </>
  );
}
