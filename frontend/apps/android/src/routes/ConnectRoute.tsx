import { useEffect, useState } from "react";
import { Stack, Text } from "@mantine/core";
import { useNavigate } from "react-router";
import { BACK_LABEL, PHONE_START_LINE } from "@beamlink/core";
import { ConnectSteps, useNexusTlsPin } from "@beamlink/features";
import {
  Beamlink,
  type RegistrationStatus,
} from "../native/beamlinkPlugin.ts";
import { androidNexusTlsPinAdapter } from "../platform/androidNexusTlsPinAdapter.ts";

export function Component() {
  const navigate = useNavigate();
  const tlsPin = useNexusTlsPin(androidNexusTlsPinAdapter);
  const [status, setStatus] = useState<RegistrationStatus | null>(null);

  useEffect(() => {
    void Beamlink.getRegistrationStatus().then(setStatus);
    let remove: (() => void) | undefined;
    void Beamlink.addListener("registrationState", setStatus).then((h) => {
      remove = () => {
        void h.remove();
      };
    });
    return () => {
      remove?.();
    };
  }, []);

  /**
   * Registers after Confirm, or again when the pin is already set.
   */
  const register = async () => {
    if (status?.hasTokens) {
      await Beamlink.reRegister();
      return;
    }
    await Beamlink.register();
  };

  return (
    <Stack gap="md" w="100%">
      <Text size="sm" c="var(--bl-text-secondary)">
        {PHONE_START_LINE}
      </Text>
      <ConnectSteps
        tlsPin={tlsPin}
        deviceState={status?.state}
        onRegister={register}
        registerAfterConfirm
        secondaryLabel={BACK_LABEL}
        onSecondary={() => navigate("/upload")}
        secondaryVariant="default"
      />
    </Stack>
  );
}
