import { TransferView } from "@beamlink/features";
import { settingsApi } from "../api/settingsApi";
import { peersApi, transferApi } from "../api/transferApi";
import { useAuthStore } from "../store/authStore";

export function Component() {
  const enabled = useAuthStore((s) => s.status === "authenticated");
  return (
    <TransferView
      transferApi={transferApi}
      peersApi={peersApi}
      settingsApi={settingsApi}
      enabled={enabled}
    />
  );
}
