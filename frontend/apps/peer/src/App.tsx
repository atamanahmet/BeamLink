import { useEffect, useState } from "react";
import { Outlet } from "react-router";
import { Text } from "@mantine/core";
import { QueryClientProvider } from "@tanstack/react-query";
import { usePlatform } from "@beamlink/platform";
import icon from "../assets/icon.png";
import StartupScreen from "./components/StartupScreen";
import { setApiBaseUrl } from "./api/client";
import { getAgentStatus } from "./api/agentApi";
import { usePeerSse } from "./hooks/usePeerSse";
import { queryClient } from "./queryClient";
import { useAuthStore } from "./store/authStore";
import { useAgentStatusStore } from "./store/agentStatusStore";

/**
 * Loads agent status and keeps SSE alive while logged in.
 */
function PeerSessionEffects() {
  const status = useAuthStore((s) => s.status);
  const setAgentStatus = useAgentStatusStore((s) => s.setAgentStatus);

  usePeerSse(setAgentStatus);

  useEffect(() => {
    if (status !== "authenticated") {
      setAgentStatus(null);
      return;
    }
    void getAgentStatus()
      .then(setAgentStatus)
      .catch(() => setAgentStatus(null));
  }, [status, setAgentStatus]);

  return null;
}

/**
 * Wires production backend URL once the platform reports ready.
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

function App() {
  const [ready, setReady] = useState(false);

  if (!ready) return <StartupScreen onReady={() => setReady(true)} />;

  return (
    <QueryClientProvider client={queryClient}>
      <BackendUrlBinder />
      <PeerSessionEffects />
      <div
        style={{
          backgroundColor: "var(--bl-background)",
          minHeight: "100vh",
        }}
      >
        <div
          style={
            {
              WebkitAppRegion: "drag",
              height: 40,
              width: "100%",
              display: "flex",
              alignItems: "center",
              paddingLeft: 16,
              paddingRight: 16,
              gap: 12,
            } as React.CSSProperties
          }
        >
          <img
            src={icon}
            alt=""
            style={
              {
                width: 20,
                height: 20,
                WebkitAppRegion: "no-drag",
              } as React.CSSProperties
            }
          />
          <Text
            size="sm"
            fw={500}
            c="var(--bl-primary-text)"
            style={{ userSelect: "none" }}
          >
            BeamLink Agent
          </Text>
        </div>
        <Outlet />
      </div>
    </QueryClientProvider>
  );
}

export default App;
