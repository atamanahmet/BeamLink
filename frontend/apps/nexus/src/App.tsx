import { useCallback, useEffect, useState } from "react";
import { Outlet } from "react-router";
import { Text } from "@mantine/core";
import { QueryClientProvider } from "@tanstack/react-query";
import { usePlatform } from "@beamlink/platform";
import StartupScreen from "./components/StartupScreen";
import icon from "../assets/icon.png";
import { setApiBaseUrl } from "./api/client";
import { bootstrapAuth } from "./bootstrapAuth";
import { queryClient } from "./queryClient";

/**
 * Wires production backend URL once the platform reports ready.
 */
function BackendUrlBinder() {
  const platform = usePlatform();

  useEffect(() => {
    const apply = (data: { status: string; backendUrl?: string }) => {
      if (data.status === "ready" && data.backendUrl) {
        setApiBaseUrl(data.backendUrl);
      }
    };
    void platform.getBackendStatus().then(apply);
    void platform.onBackendStatus(apply);
  }, [platform]);

  return null;
}

/**
 * Runs auth bootstrap after the backend URL is wired.
 */
function AuthBootstrap({ onDone }: { onDone: () => void }) {
  const platform = usePlatform();

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const status = await platform.getBackendStatus();
      if (status.status === "ready" && status.backendUrl) {
        setApiBaseUrl(status.backendUrl);
      }
      if (!cancelled) {
        await bootstrapAuth();
        onDone();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [platform, onDone]);
  return null;
}

function App() {
  const [ready, setReady] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const markAuthReady = useCallback(() => setAuthReady(true), []);

  if (!ready) return <StartupScreen onReady={() => setReady(true)} />;

  return (
    <QueryClientProvider client={queryClient}>
      <BackendUrlBinder />
      <AuthBootstrap onDone={markAuthReady} />
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
            BeamLink Nexus
          </Text>
        </div>
        {authReady ? <Outlet /> : null}
      </div>
    </QueryClientProvider>
  );
}

export default App;
