import { Group, Text } from "@mantine/core";
import { AlertCircle, CheckCircle } from "lucide-react";

type StatusMessageProps = {
  type: "success" | "error";
  message: string;
};

/**
 * Inline success or error message for settings sections.
 */
export function StatusMessage({ type, message }: StatusMessageProps) {
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
      <Text
        size="sm"
        c={isSuccess ? "var(--bl-success-text)" : "var(--bl-danger-text)"}
      >
        {message}
      </Text>
    </Group>
  );
}
