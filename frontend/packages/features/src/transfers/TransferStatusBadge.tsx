import { Badge } from "@mantine/core";

/**
 * Maps a transfer status string to a Mantine Badge color.
 */
function statusColor(status: string): string {
  if (status === "COMPLETED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "ACTIVE") return "info";
  if (
    status === "PENDING" ||
    status === "PAUSED" ||
    status === "PARTIAL"
  ) {
    return "warning";
  }
  return "gray";
}

type TransferStatusBadgeProps = {
  status: string;
};

/**
 * Status badge using theme role colors when available.
 */
export function TransferStatusBadge({ status }: TransferStatusBadgeProps) {
  return (
    <Badge color={statusColor(status)} variant="light" size="sm">
      {status}
    </Badge>
  );
}
