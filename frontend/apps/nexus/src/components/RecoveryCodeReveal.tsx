import { useState } from "react";
import { Button, Checkbox, Code, Group, Stack, Text } from "@mantine/core";
import { recoveryCopy } from "../recoveryCopy.ts";

type RecoveryCodeRevealProps = {
  code: string;
  warning?: string;
  requireSaved?: boolean;
  continueLabel?: string;
  onContinue?: () => void;
};

/**
 * Shows a recovery code once with copy. Optional saved checkbox gates Continue.
 */
export function RecoveryCodeReveal({
  code,
  warning = recoveryCopy.resetWarning,
  requireSaved = false,
  continueLabel = recoveryCopy.continue,
  onContinue,
}: RecoveryCodeRevealProps) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
  };

  return (
    <Stack gap="md">
      <Text size="sm" c="var(--bl-text-secondary)">
        {warning}
      </Text>
      <Code block ff="monospace">
        {code}
      </Code>
      <Group>
        <Button variant="light" size="sm" onClick={() => void copy()}>
          {copied ? recoveryCopy.copied : recoveryCopy.copy}
        </Button>
      </Group>
      {requireSaved ? (
        <>
          <Checkbox
            label={recoveryCopy.savedCheckbox}
            checked={saved}
            onChange={(event) => setSaved(event.currentTarget.checked)}
          />
          <Button fullWidth disabled={!saved} onClick={onContinue}>
            {continueLabel}
          </Button>
        </>
      ) : null}
    </Stack>
  );
}
