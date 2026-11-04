import { useState } from "react";
import { Badge, Button, Group, Modal, Paper, Stack, Text } from "@mantine/core";
import {
  TransferProgressBar,
  TransfersEmpty,
  formatBytes,
  mapTransferErrorCode,
  useTransferActions,
  useTransfersQuery,
} from "@beamlink/features";
import { transferApi } from "../api/transferApi";
import {
  orderPhoneTransfers,
  statusBadgeColor,
  statusWord,
} from "../transfers/phoneTransferMap.ts";

/**
 * Compact phone transfer cards on the shared transfer hooks.
 */
export function PhoneTransferList() {
  const { data } = useTransfersQuery(transferApi);
  const { cancel, resume } = useTransferActions(transferApi);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const list = orderPhoneTransfers(data ?? []);

  return (
    <Stack gap="lg" w="100%">
      {list.length === 0 ? (
        <TransfersEmpty />
      ) : (
        <Stack gap="md">
          {list.map((t) => {
            const direction = t.id === "receive" ? "receive" : "send";
            const peer = direction === "send" ? t.targetDeviceId : t.sourceDeviceId;
            const total = t.totalSize || 0;
            const progress =
              total > 0
                ? Math.min(100, Math.round((t.confirmedBytes / total) * 100))
                : 0;
            const active =
              t.status === "ACTIVE" || t.status === "PAUSED";
            return (
              <Paper key={t.id} withBorder p="md" radius="md">
                <Stack gap="sm">
                  <Group justify="space-between" align="center" wrap="nowrap" gap="sm">
                    <Text fw={600} size="md" c="var(--bl-text-primary)" lineClamp={1}>
                      {t.name}
                    </Text>
                    <Badge
                      color={statusBadgeColor(t.status)}
                      variant="light"
                      size="sm"
                    >
                      {statusWord(t.status, direction)}
                    </Badge>
                  </Group>
                  <Text size="sm" c="var(--bl-text-secondary)">
                    {peer || "Peer"}
                  </Text>
                  <TransferProgressBar
                    status={t.status}
                    progress={progress}
                    bytesLabel={`${formatBytes(t.confirmedBytes)} / ${formatBytes(total)}`}
                    compact
                  />
                  {t.status === "FAILED" && t.failureReason ? (
                    <Text size="sm" c="var(--bl-danger-text)">
                      {t.failureReason}
                    </Text>
                  ) : null}
                  {t.status === "PAUSED" && t.failureReason ? (
                    <Text size="sm" c="var(--bl-danger-text)">
                      {t.failureReason === "INSUFFICIENT_DISK"
                        ? mapTransferErrorCode("INSUFFICIENT_DISK")
                        : t.failureReason}
                    </Text>
                  ) : null}
                  {active ? (
                    <>
                      {t.canResume ? (
                        <Button
                          variant="default"
                          loading={resume.isPending}
                          onClick={() =>
                            resume.mutate({ id: t.id, type: "SINGLE" })
                          }
                        >
                          Resume
                        </Button>
                      ) : null}
                      <Button
                        variant="default"
                        onClick={() => setConfirmId(t.id)}
                      >
                        Cancel
                      </Button>
                    </>
                  ) : null}
                </Stack>
              </Paper>
            );
          })}
        </Stack>
      )}
      <Modal
        opened={confirmId != null}
        onClose={() => setConfirmId(null)}
        title="Cancel this transfer?"
      >
        <Stack gap="md">
          <Button
            fullWidth
            color="danger"
            loading={cancel.isPending}
            onClick={() => {
              if (confirmId == null) return;
              cancel.mutate(
                { id: confirmId, type: "SINGLE" },
                { onSettled: () => setConfirmId(null) },
              );
            }}
          >
            Cancel transfer
          </Button>
          <Button variant="default" onClick={() => setConfirmId(null)}>
            Keep
          </Button>
        </Stack>
      </Modal>
    </Stack>
  );
}
