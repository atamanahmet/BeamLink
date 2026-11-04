import { Stack, Text } from "@mantine/core";

type ErrorWithRefProps = {
  message: string;
  correlationId?: string | null;
};

/**
 * Error text plus a selectable Ref line when a correlation id exists.
 */
export function ErrorWithRef({ message, correlationId }: ErrorWithRefProps) {
  const ref = correlationId && correlationId.length > 0 ? correlationId : null;
  return (
    <Stack gap={4}>
      <Text size="sm" component="span" c="inherit">
        {message}
      </Text>
      {ref ? (
        <Text
          size="xs"
          c="var(--bl-text-muted)"
          ff="monospace"
          style={{ userSelect: "all" }}
        >
          Ref: {ref}
        </Text>
      ) : null}
    </Stack>
  );
}
