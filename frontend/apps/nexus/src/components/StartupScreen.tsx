import { useEffect, useRef, useState } from "react";
import { Loader, Stack, Text, Title } from "@mantine/core";
import { usePlatform } from "@beamlink/platform";
import icon from "../assets/icon.png";

type Status = "starting" | "ready" | "error";

interface Props {
  onReady: () => void;
}

/**
 * Shows backend start progress until the app can open.
 */
export default function StartupScreen({ onReady }: Props) {
  const platform = usePlatform();
  const [status, setStatus] = useState<Status>("starting");
  const [errorMsg, setErrorMsg] = useState("");
  const onReadyRef = useRef(onReady);

  useEffect(() => {
    onReadyRef.current = onReady;
  }, [onReady]);

  useEffect(() => {
    const handle = (data: {
      status: string;
      backendUrl?: string;
      message?: string;
    }) => {
      if (data.status === "ready") {
        setStatus("ready");
        setTimeout(() => onReadyRef.current(), 400);
      } else if (data.status === "error") {
        setStatus("error");
        setErrorMsg(data.message ?? "Unknown error");
      }
    };

    void platform.getBackendStatus().then(handle);
    void platform.onBackendStatus(handle);
  }, [platform]);

  let statusText = "Starting backend...";
  let statusColor = "var(--bl-text-secondary)";
  if (status === "ready") {
    statusText = "Ready";
    statusColor = "var(--bl-success-text)";
  } else if (status === "error") {
    statusText = "Startup failed";
    statusColor = "var(--bl-danger-text)";
  }

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "var(--bl-background)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Stack align="center" gap="md">
        <img
          src={icon}
          alt=""
          style={{ width: 72, height: 72, borderRadius: 16 }}
        />
        <Title order={3} c="var(--bl-primary-text)">
          BeamLink Nexus
        </Title>
        {status === "starting" ? <Loader color="brand" type="dots" /> : null}
        <Text size="sm" c={statusColor}>
          {statusText}
        </Text>
        {status === "error" ? (
          <Text size="xs" c="var(--bl-text-muted)" ta="center" maw={320}>
            {errorMsg}
          </Text>
        ) : null}
      </Stack>
    </div>
  );
}
