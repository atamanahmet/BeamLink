import { Group, Text } from "@mantine/core";
import { AlertCircle, CheckCircle } from "lucide-react";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";

type StatusMessageProps = {
  type: "success" | "error";
  message: string;
  correlationId?: string | null;
};

/**
 * Inline success or error message for settings sections.
 */
export function StatusMessage({
  type,
  message,
  correlationId,
}: StatusMessageProps) {
  const isSuccess = type === "success";
  return (
    <Group
      gap="xs"
      wrap="nowrap"
      px="sm"
      py="xs"
      style={{
        borderRadius: 8,
        border: `1px solid ${
          isSuccess ? "var(--bl-success)" : "var(--bl-danger)"
        }`,
        backgroundColor: isSuccess
          ? "var(--bl-success-subtle)"
          : "var(--bl-danger-subtle)",
      }}
    >
      {isSuccess ? (
        <CheckCircle size={16} color="var(--bl-success-text)" />
      ) : (
        <AlertCircle size={16} color="var(--bl-danger-text)" />
      )}
      {isSuccess ? (
        <Text size="sm" c="var(--bl-success-text)">
          {message}
        </Text>
      ) : (
        <Text component="div" size="sm" c="var(--bl-danger-text)">
          <ErrorWithRef message={message} correlationId={correlationId} />
        </Text>
      )}
    </Group>
  );
}
