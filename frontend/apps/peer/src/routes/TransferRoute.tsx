import { TransferView } from "@beamlink/features";
import { settingsApi } from "../api/settingsApi";
import { peersApi, transferApi } from "../api/transferApi";
import { useDeviceStatusStore } from "../store/deviceStatusStore";

export function Component() {
  const localDeviceId = useDeviceStatusStore((s) => s.deviceStatus?.deviceId ?? null);
  return (
    <TransferView
      transferApi={transferApi}
      peersApi={peersApi}
      settingsApi={settingsApi}
      enabled={true}
      localDeviceId={localDeviceId}
    />
  );
}
