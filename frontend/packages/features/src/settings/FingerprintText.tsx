import { Text } from "@mantine/core";
import { formatTlsPin } from "./formatTlsPin.ts";

type FingerprintTextProps = {
  value: string;
  label: string;
};

/**
 * Monospace fingerprint block.
 */
export function FingerprintText({
  value,
  label,
}: FingerprintTextProps) {
  return (
    <Text
      size="sm"
      c="var(--bl-text-secondary)"
      ff="monospace"
      style={{ whiteSpace: "pre-line" }}
    >
      {label}: {formatTlsPin(value)}
    </Text>
  );
}
