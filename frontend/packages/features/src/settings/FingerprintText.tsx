import { Text } from "@mantine/core";

type FingerprintTextProps = {
  value: string;
  label: string;
};

/**
 * Monospace fingerprint line.
 */
export function FingerprintText({
  value,
  label,
}: FingerprintTextProps) {
  return (
    <Text size="sm" c="var(--bl-text-secondary)" ff="monospace">
      {label}: {value}
    </Text>
  );
}
