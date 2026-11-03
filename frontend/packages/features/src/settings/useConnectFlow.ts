import { createContext, useCallback, useContext, useState } from "react";
import { DEVICE_STATE_APPROVED, connectNeedsAction } from "@beamlink/core";
import type { NexusTlsPinAdapter } from "./nexusTlsPin.ts";
import { useNexusTlsPin } from "./useNexusTlsPin.ts";

const ConnectOpenContext = createContext<() => void>(() => {});

/**
 * Opens the shared Connect modal from Settings.
 */
export function useConnectOpen(): () => void {
  return useContext(ConnectOpenContext);
}

export const ConnectOpenProvider = ConnectOpenContext.Provider;

export type ConnectFlow = {
  open: boolean;
  openModal: () => void;
  later: () => void;
  tlsPin: ReturnType<typeof useNexusTlsPin>;
};

/**
 * Opens the Connect modal when the device needs action. Later keeps it closed.
 */
export function useConnectFlow(
  adapter: NexusTlsPinAdapter,
  deviceState: string | undefined,
): ConnectFlow {
  const tlsPin = useNexusTlsPin(adapter);
  const [dismissed, setDismissed] = useState(false);

  if (deviceState === DEVICE_STATE_APPROVED && dismissed) {
    setDismissed(false);
  }

  const open = connectNeedsAction(deviceState) && !dismissed;

  const openModal = useCallback(() => {
    setDismissed(false);
  }, []);

  const later = useCallback(() => {
    setDismissed(true);
  }, []);

  return { open, openModal, later, tlsPin };
}
