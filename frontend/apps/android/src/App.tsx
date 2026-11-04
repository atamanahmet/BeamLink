import { useEffect, useRef } from "react";
import { Outlet, useNavigate } from "react-router";
import { QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { usePlatform } from "@beamlink/platform";
import { setApiBaseUrl } from "./api/client";
import { queryClient } from "./queryClient";
import { Beamlink } from "./native/beamlinkPlugin.ts";
import { phoneNeedsConnect } from "./status/connectionStatus.ts";
import {
  applyProgress,
  subscribePhoneTransfers,
} from "./transfers/phoneTransferStore.ts";

/**
 * Wires backend URL from platform or localStorage.
 */
function BackendUrlBinder() {
  const platform = usePlatform();

  useEffect(() => {
    void platform.getBackendStatus().then((data) => {
      if (data.status === "ready" && data.backendUrl) {
        setApiBaseUrl(data.backendUrl);
      }
    });
    void platform.onBackendStatus((data) => {
      if (data.status === "ready" && data.backendUrl) {
        setApiBaseUrl(data.backendUrl);
      }
    });
  }, [platform]);

  return null;
}

/**
 * Keeps the transfers query in sync with native send and receive progress.
 */
function PhoneTransferSync() {
  const client = useQueryClient();

  useEffect(() => {
    const unsub = subscribePhoneTransfers(() => {
      void client.invalidateQueries({ queryKey: ["transfers"] });
    });
    const pullSlots = () => {
      void Beamlink.getTransferSlots().then((r) => {
        for (const slot of r.slots) {
          applyProgress(slot);
        }
      });
    };
    pullSlots();
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        pullSlots();
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    let remove: (() => void) | undefined;
    void Beamlink.addListener("sendProgress", (event) => {
      applyProgress(event);
    }).then((h) => {
      remove = () => {
        void h.remove();
      };
    });
    return () => {
      unsub();
      document.removeEventListener("visibilitychange", onVisible);
      remove?.();
    };
  }, [client]);

  return null;
}

/**
 * Opens transfers when a disk-pause notification is tapped.
 */
function OpenTransfersListener() {
  const navigate = useNavigate();

  useEffect(() => {
    let remove: (() => void) | undefined;
    void Beamlink.addListener("openTransfers", () => {
      navigate("/transfers");
    }).then((h) => {
      remove = () => {
        void h.remove();
      };
    });
    return () => {
      remove?.();
    };
  }, [navigate]);

  return null;
}

/**
 * Opens Connect on start only when the phone is not connected.
 */
function ConnectGate() {
  const navigate = useNavigate();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void Promise.all([
      Beamlink.getRegistrationStatus(),
      Beamlink.getNexusTlsStatus(),
    ]).then(([reg, tls]) => {
      if (phoneNeedsConnect(reg.state, reg.hasTokens, tls.pinSet)) {
        navigate("/connect", { replace: true });
      }
    });
  }, [navigate]);

  return null;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BackendUrlBinder />
      <PhoneTransferSync />
      <OpenTransfersListener />
      <ConnectGate />
      <div
        style={{
          backgroundColor: "var(--bl-background)",
          height: "100%",
          minHeight: "100%",
        }}
      >
        <Outlet />
      </div>
    </QueryClientProvider>
  );
}

export default App;
