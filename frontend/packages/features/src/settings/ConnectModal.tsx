import { Modal } from "@mantine/core";
import { CONNECT_LABEL, LATER_LABEL } from "@beamlink/core";
import { ConnectSteps } from "./ConnectSteps.tsx";
import type { NexusTlsPinControls } from "./useNexusTlsPin.ts";

export type ConnectModalProps = {
  open: boolean;
  tlsPin: NexusTlsPinControls;
  deviceState: string | undefined;
  onRegister?: () => Promise<void>;
  registerAfterConfirm?: boolean;
  onLater: () => void;
};

/**
 * Connect modal. Later closes. Confirm of the fingerprint is a click.
 */
export function ConnectModal({
  open,
  tlsPin,
  deviceState,
  onRegister,
  registerAfterConfirm = false,
  onLater,
}: ConnectModalProps) {
  return (
    <Modal
      opened={open}
      onClose={onLater}
      title={CONNECT_LABEL}
      centered
      radius="md"
      withCloseButton={false}
      closeOnClickOutside={false}
      trapFocus
    >
      <ConnectSteps
        tlsPin={tlsPin}
        deviceState={deviceState}
        onRegister={onRegister}
        registerAfterConfirm={registerAfterConfirm}
        secondaryLabel={LATER_LABEL}
        onSecondary={onLater}
      />
    </Modal>
  );
}
