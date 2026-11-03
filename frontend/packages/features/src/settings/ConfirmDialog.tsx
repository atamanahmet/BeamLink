import { useEffect, useRef, type ReactNode } from "react";
import {
  Button,
  Checkbox,
  Group,
  List,
  Modal,
  Stack,
  Text,
} from "@mantine/core";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";
import {
  destructiveDialogEscapeCloses,
  destructiveDialogPhase,
} from "./destructiveDialogPhase.ts";

export type ConfirmDialogProps = {
  open: boolean;
  title: string;
  deleted?: string[];
  kept?: string[];
  body?: string;
  confirmLabel?: string;
  confirmColor?: string;
  pending?: boolean;
  error?: string | null;
  correlationId?: string | null;
  result?: string | null;
  forceAllowed?: boolean;
  forceChecked?: boolean;
  onForceChecked?: (checked: boolean) => void;
  canConfirm?: boolean;
  children?: ReactNode;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * Plain confirm dialog. Phases idle, pending, success, failure.
 */
export function ConfirmDialog({
  open,
  title,
  deleted,
  kept,
  body,
  confirmLabel = "Confirm",
  confirmColor,
  pending = false,
  error,
  correlationId,
  result,
  forceAllowed = false,
  forceChecked = false,
  onForceChecked,
  canConfirm = true,
  children,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const phase = destructiveDialogPhase(pending, result, error);
  const escapeCloses = destructiveDialogEscapeCloses(phase);
  const allowConfirm = (phase === "idle" || phase === "failure") && canConfirm;
  const doneRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (phase === "success") {
      doneRef.current?.focus();
    }
  }, [phase]);

  const handleClose = () => {
    if (phase === "pending") {
      return;
    }
    onCancel();
  };

  return (
    <Modal
      opened={open}
      onClose={handleClose}
      title={title}
      centered
      radius="md"
      role="alertdialog"
      withCloseButton={false}
      closeOnClickOutside={false}
      closeOnEscape={escapeCloses}
      trapFocus
    >
      <Stack gap="md">
        {body ? (
          <Text c="var(--bl-text-primary)" style={{ whiteSpace: "pre-line" }}>
            {body}
          </Text>
        ) : null}
        {deleted && deleted.length > 0 ? (
          <div>
            <Text size="sm" fw={600} c="var(--bl-text-primary)">
              Deleted
            </Text>
            <List size="sm" c="var(--bl-text-secondary)" mt={4}>
              {deleted.map((item) => (
                <List.Item key={item}>{item}</List.Item>
              ))}
            </List>
          </div>
        ) : null}
        {kept && kept.length > 0 ? (
          <div>
            <Text size="sm" fw={600} c="var(--bl-text-primary)">
              Kept
            </Text>
            <List size="sm" c="var(--bl-text-secondary)" mt={4}>
              {kept.map((item) => (
                <List.Item key={item}>{item}</List.Item>
              ))}
            </List>
          </div>
        ) : null}
        {children}
        {error ? (
          <Text
            size="sm"
            c="var(--bl-danger-text)"
            role="alert"
            aria-live="assertive"
            component="div"
          >
            <ErrorWithRef message={error} correlationId={correlationId} />
          </Text>
        ) : null}
        {forceAllowed && phase !== "success" ? (
          <Checkbox
            label="Reset anyway"
            checked={forceChecked}
            disabled={phase === "pending"}
            onChange={(event) => onForceChecked?.(event.currentTarget.checked)}
          />
        ) : null}
        {result ? (
          <Text
            size="sm"
            c="var(--bl-success-text)"
            role="status"
            aria-live="polite"
          >
            {result}
          </Text>
        ) : null}
        <Group justify="flex-end" gap="sm">
          {phase === "success" ? (
            <Button ref={doneRef} data-autofocus onClick={onCancel}>
              Done
            </Button>
          ) : (
            <>
              <Button
                variant="default"
                onClick={onCancel}
                disabled={phase === "pending"}
              >
                Cancel
              </Button>
              <Button
                color={confirmColor}
                onClick={onConfirm}
                disabled={!allowConfirm}
                loading={phase === "pending"}
                aria-busy={phase === "pending"}
              >
                {confirmLabel}
              </Button>
            </>
          )}
        </Group>
      </Stack>
    </Modal>
  );
}
