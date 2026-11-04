import { Box } from "@mantine/core";
import { ErrorWithRef } from "../problem/ErrorWithRef.tsx";

export type UploadBannerKind = "error" | "notice";

export type UploadBannerProps = {
  message: string;
  kind?: UploadBannerKind;
  correlationId?: string | null;
};

/**
 * Upload page banner. Contrast tokens, no Mantine selection colors.
 */
export function UploadBanner({
  message,
  kind = "error",
  correlationId,
}: UploadBannerProps) {
  const error = kind === "error";
  return (
    <Box
      px="sm"
      py="xs"
      style={{
        borderRadius: 8,
        color: error ? "var(--bl-danger-text)" : "var(--bl-text-primary)",
        backgroundColor: error
          ? "var(--bl-danger-subtle)"
          : "var(--bl-warning-subtle)",
        border: `1px solid ${error ? "var(--bl-danger)" : "var(--bl-warning)"}`,
      }}
    >
      {error ? (
        <ErrorWithRef message={message} correlationId={correlationId} />
      ) : (
        message
      )}
    </Box>
  );
}
