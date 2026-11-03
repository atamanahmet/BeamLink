import { useState } from "react";
import { TextInput } from "@mantine/core";
import { ConfirmDialog } from "./ConfirmDialog.tsx";
import { destructiveDialogPhase } from "./destructiveDialogPhase.ts";

export const RESET_CONFIRM_WORD = "RESET";

export type TypedConfirmDialogProps = {
  open: boolean;
  title: string;
  deleted?: string[];
  kept?: string[];
  body?: string;
  confirmWord?: string | null;
  confirmLabel?: string;
  pending?: boolean;
  error?: string | null;
  correlationId?: string | null;
  result?: string | null;
  forceAllowed?: boolean;
  forceChecked?: boolean;
  onForceChecked?: (checked: boolean) => void;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * Confirm dialog that also asks for a typed word.
 */
export function TypedConfirmDialog({
  open,
  title,
  deleted,
  kept,
  body,
  confirmWord,
  confirmLabel = "Confirm",
  pending = false,
  error,
  correlationId,
  result,
  forceAllowed = false,
  forceChecked = false,
  onForceChecked,
  onConfirm,
  onCancel,
}: TypedConfirmDialogProps) {
  const word = confirmWord === undefined ? RESET_CONFIRM_WORD : confirmWord;
  const requireWord = Boolean(word);
  const [typed, setTyped] = useState("");
  const phase = destructiveDialogPhase(pending, result, error);
  const canConfirm = !requireWord || typed === word;

  return (
    <ConfirmDialog
      open={open}
      title={title}
      deleted={deleted}
      kept={kept}
      body={body}
      confirmLabel={confirmLabel}
      confirmColor="danger"
      pending={pending}
      error={error}
      correlationId={correlationId}
      result={result}
      forceAllowed={forceAllowed}
      forceChecked={forceChecked}
      onForceChecked={onForceChecked}
      canConfirm={canConfirm}
      onConfirm={onConfirm}
      onCancel={onCancel}
    >
      {requireWord && phase !== "success" ? (
        <TextInput
          label={`Type ${word} to confirm`}
          value={typed}
          disabled={phase === "pending"}
          onChange={(event) => setTyped(event.currentTarget.value)}
        />
      ) : null}
    </ConfirmDialog>
  );
}
