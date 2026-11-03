import { useState } from "react";
import { Button, Group, Paper, Stack, Text } from "@mantine/core";
import { problemFromUnknown, type SettingsApi } from "@beamlink/core";
import { AlertTriangle } from "lucide-react";
import { isResetBusy, resetErrorMessage } from "./resetErrorMessage.ts";
import { TypedConfirmDialog } from "./TypedConfirmDialog.tsx";

type ConfirmKind = "reRegister" | "network" | "factory";

type DangerZoneSectionProps = {
  settingsApi: SettingsApi;
  kind: "device" | "nexus";
  onAfterFactoryReset?: () => void | Promise<void>;
};

const DEVICE_REREGISTER_DELETED = [
  "This device identity, tokens, nonce, nexus public key, and peer cache",
  "Best-effort remove of this device on nexus",
];
const DEVICE_REREGISTER_KEPT = [
  "Uploaded and received files",
  "Transfers, logs, settings, and the nexus URL",
];
const DEVICE_FACTORY_DELETED = [
  "All database rows",
  "In-memory state",
  "Temp directory contents",
  "Remove the old entry on the nexus.",
];
const DEVICE_FACTORY_KEPT = [
  "Schema and Flyway history",
  "Uploaded and received files",
];
const NEXUS_NETWORK_DELETED = [
  "Devices, transfers, and logs",
  "WebSocket sessions, caches, and nonces",
];
const NEXUS_NETWORK_KEPT = [
  "Admin account and JWT keys",
  "Database file and Flyway history",
];
const NEXUS_FACTORY_DELETED = [
  "Devices, transfers, and logs",
  "WebSocket sessions, caches, and nonces",
  "Admin account. All admin sessions die",
];
const NEXUS_FACTORY_KEPT = ["JWT keys, database file, and Flyway history"];

/**
 * Danger zone actions for device or nexus reset.
 */
export function DangerZoneSection({
  settingsApi,
  kind,
  onAfterFactoryReset,
}: DangerZoneSectionProps) {
  const [confirmKind, setConfirmKind] = useState<ConfirmKind | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [forceAllowed, setForceAllowed] = useState(false);
  const [forceChecked, setForceChecked] = useState(false);

  const close = () => {
    if (pending) return;
    const runAfterFactory = confirmKind === "factory" && result != null;
    setConfirmKind(null);
    setError(null);
    setCorrelationId(null);
    setResult(null);
    setForceAllowed(false);
    setForceChecked(false);
    if (runAfterFactory) {
      void onAfterFactoryReset?.();
    }
  };

  const openConfirm = (kind: ConfirmKind) => {
    setError(null);
    setCorrelationId(null);
    setResult(null);
    setForceAllowed(false);
    setForceChecked(false);
    setConfirmKind(kind);
  };

  /**
   * Runs the selected reset. After 409, a checkbox retry uses force=true.
   */
  const run = async () => {
    if (!confirmKind) return;
    setPending(true);
    setError(null);
    setCorrelationId(null);
    const force = forceChecked;
    try {
      if (confirmKind === "reRegister") {
        const response = await settingsApi.reRegister?.(force);
        setResult(
          response?.message ?? "Identity wiped. Device will connect to a nexus again.",
        );
        return;
      }
      if (confirmKind === "network") {
        await settingsApi.resetNetworkData?.(force);
        setResult("Network data reset complete.");
        return;
      }
      await settingsApi.factoryReset?.(force);
      setResult("Factory reset complete.");
    } catch (err: unknown) {
      setError(resetErrorMessage(err));
      setCorrelationId(problemFromUnknown(err).correlationId);
      if (isResetBusy(err)) {
        setForceAllowed(true);
      }
    } finally {
      setPending(false);
    }
  };

  const copy =
    confirmKind === "reRegister"
      ? { title: "Wipe identity and connect again", deleted: DEVICE_REREGISTER_DELETED, kept: DEVICE_REREGISTER_KEPT }
      : confirmKind === "network"
        ? { title: "Reset network data", deleted: NEXUS_NETWORK_DELETED, kept: NEXUS_NETWORK_KEPT }
        : kind === "device"
          ? { title: "Factory reset", deleted: DEVICE_FACTORY_DELETED, kept: DEVICE_FACTORY_KEPT }
          : { title: "Factory reset", deleted: NEXUS_FACTORY_DELETED, kept: NEXUS_FACTORY_KEPT };

  return (
    <Paper
      withBorder
      p="lg"
      radius="md"
      style={{
        backgroundColor: "var(--bl-surface)",
        borderColor: "var(--bl-danger)",
      }}
    >
      <Stack gap="md">
        <Group gap="xs">
          <AlertTriangle size={16} color="var(--bl-danger-text)" />
          <Text
            size="sm"
            fw={600}
            tt="uppercase"
            c="var(--bl-danger-text)"
            style={{ letterSpacing: "0.08em" }}
          >
            Danger zone
          </Text>
        </Group>
        <Text size="xs" c="var(--bl-text-muted)">
          These actions cannot be undone.
        </Text>
        <Group gap="sm">
          {kind === "device" && settingsApi.reRegister ? (
            <Button
              color="danger"
              variant="light"
              onClick={() => openConfirm("reRegister")}
            >
              Re-register with nexus
            </Button>
          ) : null}
          {kind === "nexus" && settingsApi.resetNetworkData ? (
            <Button
              color="danger"
              variant="light"
              onClick={() => openConfirm("network")}
            >
              Reset network data
            </Button>
          ) : null}
          {settingsApi.factoryReset ? (
            <Button
              color="danger"
              onClick={() => openConfirm("factory")}
            >
              Factory reset
            </Button>
          ) : null}
        </Group>
      </Stack>
      <TypedConfirmDialog
        key={confirmKind ?? "closed"}
        open={confirmKind !== null}
        title={copy.title}
        deleted={copy.deleted}
        kept={copy.kept}
        pending={pending}
        error={error}
        correlationId={correlationId}
        result={result}
        forceAllowed={forceAllowed}
        forceChecked={forceChecked}
        onForceChecked={setForceChecked}
        onConfirm={() => void run()}
        onCancel={close}
      />
    </Paper>
  );
}
