import { useEffect, useState } from "react";
import { Button, Group, NumberInput, Stack, Switch, Text } from "@mantine/core";
import { useNavigate } from "react-router";
import { ErrorWithRef, FingerprintText, useNexusTlsPin } from "@beamlink/features";
import {
  bytesToGib,
  CONNECT_LABEL,
  DEVICE_KEY_LABEL,
  gibToBytes,
  problemFromUnknown,
} from "@beamlink/core";
import {
  Beamlink,
  type RegistrationStatus,
} from "../native/beamlinkPlugin.ts";
import { androidNexusTlsPinAdapter } from "../platform/androidNexusTlsPinAdapter.ts";
import { connectionStatusWords } from "../status/connectionStatus.ts";

/**
 * Connection and receive settings for the Android lite phone.
 */
export function NexusSettingsSection() {
  const navigate = useNavigate();
  const tlsPin = useNexusTlsPin(androidNexusTlsPinAdapter);
  const [status, setStatus] = useState<RegistrationStatus | null>(null);
  const [receiveOn, setReceiveOn] = useState(false);
  const [minFreeGib, setMinFreeGib] = useState(1);
  const [minFreeError, setMinFreeError] = useState("");
  const [minFreeBusy, setMinFreeBusy] = useState(false);

  useEffect(() => {
    void Beamlink.getRegistrationStatus().then(setStatus);
    let remove: (() => void) | undefined;
    void Beamlink.addListener("registrationState", setStatus).then((h) => {
      remove = () => {
        void h.remove();
      };
    });
    const pullReceive = () => {
      void Beamlink.getReceiveEnabled().then((r) => setReceiveOn(r.enabled));
    };
    pullReceive();
    void Beamlink.getMinFreeSpaceBytes().then((r) => {
      setMinFreeGib(bytesToGib(r.value));
    });
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        pullReceive();
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    let removeToggle: (() => void) | undefined;
    void Beamlink.addListener("receiveToggle", (event) => {
      setReceiveOn(event.enabled);
    }).then((h) => {
      removeToggle = () => {
        void h.remove();
      };
    });
    return () => {
      document.removeEventListener("visibilitychange", onVisible);
      remove?.();
      removeToggle?.();
    };
  }, []);

  const statusWords = connectionStatusWords(
    status?.state,
    !!status?.hasTokens,
    tlsPin.status.pinSet,
  );

  return (
    <Stack gap="lg">
      <Stack gap="sm">
        <Text fw={600} size="md" c="var(--bl-text-primary)">
          Registration
        </Text>
        <Button variant="default" fullWidth onClick={() => navigate("/connect")}>
          {CONNECT_LABEL}
        </Button>
        <Text size="sm" c="var(--bl-text-secondary)">
          {statusWords}
        </Text>
        {status?.fingerprint ? (
          <FingerprintText label={DEVICE_KEY_LABEL} value={status.fingerprint} />
        ) : null}
      </Stack>

      <Stack gap="sm">
        <Text fw={600} size="md" c="var(--bl-text-primary)">
          Receive
        </Text>
        <Switch
          label="Receive files"
          checked={receiveOn}
          onChange={(e) => {
            const next = e.currentTarget.checked;
            setReceiveOn(next);
            void Beamlink.setReceiveEnabled({ enabled: next });
          }}
        />
        <Text size="sm" c="var(--bl-text-secondary)">
          Off until you turn it on.
        </Text>
      </Stack>

      <Stack gap="sm">
        <Text fw={600} size="md" c="var(--bl-text-primary)">
          Advanced
        </Text>
        <Text size="sm" c="var(--bl-text-secondary)">
          Disk reserve. Default is 1 GiB.
        </Text>
        <Group gap="xs">
          <NumberInput
            min={0.25}
            max={1024}
            step={0.25}
            decimalScale={2}
            value={minFreeGib}
            onChange={(val) => {
              if (typeof val === "number") {
                setMinFreeGib(val);
              }
            }}
            w={128}
          />
          <Text size="sm" c="var(--bl-text-secondary)">
            GiB
          </Text>
          <Button
            loading={minFreeBusy}
            onClick={() => {
              void (async () => {
                setMinFreeBusy(true);
                setMinFreeError("");
                try {
                  await Beamlink.setMinFreeSpaceBytes({
                    value: gibToBytes(minFreeGib),
                  });
                } catch (err: unknown) {
                  const problem = problemFromUnknown(err);
                  setMinFreeError(
                    problem.detail ??
                      (err instanceof Error ? err.message : "Save failed"),
                  );
                } finally {
                  setMinFreeBusy(false);
                }
              })();
            }}
          >
            Save
          </Button>
          <Button
            variant="default"
            onClick={() => {
              setMinFreeGib(1);
              void Beamlink.setMinFreeSpaceBytes({
                value: gibToBytes(1),
              });
            }}
          >
            Reset to default
          </Button>
        </Group>
        {minFreeError ? (
          <ErrorWithRef message={minFreeError} correlationId={null} />
        ) : null}
      </Stack>
    </Stack>
  );
}
