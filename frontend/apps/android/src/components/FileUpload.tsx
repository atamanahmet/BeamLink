import { useEffect, useState } from "react";
import { Alert, Button, List, Stack, Text } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { usePlatform } from "@beamlink/platform";
import { ErrorWithRef, PeerSelect, UploadBanner, type PeerOption } from "@beamlink/features";
import { problemFromUnknown, type SharedFileItem } from "@beamlink/core";
import { peersApi } from "../api/transferApi";
import {
  Beamlink,
  type RegistrationStatus,
  type SendProgressEvent,
} from "../native/beamlinkPlugin.ts";
import { rememberSendMeta } from "../transfers/phoneTransferStore.ts";

interface FileUploadProps {
  onTransferStarted: () => void;
}

/**
 * Send screen. Share and picker both land as SharedFileItem refs.
 */
export const FileUpload = ({ onTransferStarted }: FileUploadProps) => {
  const platform = usePlatform();
  const navigate = useNavigate();

  const [selectedPeer, setSelectedPeer] = useState(
    () => localStorage.getItem("lastSelectedPeer") ?? "",
  );
  const [message, setMessage] = useState("");
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<SharedFileItem[]>([]);
  const [reg, setReg] = useState<RegistrationStatus | null>(null);
  const [picking, setPicking] = useState(false);

  const peersQuery = useQuery({
    queryKey: ["peers"],
    queryFn: () => peersApi.getPeers(),
    enabled: !!reg?.hasTokens,
    refetchOnMount: "always",
    refetchInterval: false,
    retry: false,
  });

  const peers = peersQuery.data ?? [];
  const selectedPeerObject = peers.find((p) => p.id === selectedPeer);
  const isOffline = !!selectedPeerObject && !selectedPeerObject.online;
  const waitingApproval = !reg?.hasTokens;

  useEffect(() => {
    void Beamlink.getRegistrationStatus().then(setReg);
    let removeReg: (() => void) | undefined;
    let removeProgress: (() => void) | undefined;
    void Beamlink.addListener("registrationState", (event) => {
      setReg(event);
    }).then((h) => {
      removeReg = () => {
        void h.remove();
      };
    });
    void Beamlink.addListener("sendProgress", (event: SendProgressEvent) => {
      if (event.direction === "receive") {
        return;
      }
      if (event.state === "completed") {
        setIsSending(false);
        setPendingFiles([]);
      } else if (event.state === "error" || event.state === "cancelled") {
        setIsSending(false);
        setMessage(event.error ?? event.state);
      }
    }).then((h) => {
      removeProgress = () => {
        void h.remove();
      };
    });
    return () => {
      removeReg?.();
      removeProgress?.();
    };
  }, []);

  useEffect(() => {
    if (!platform.onSharedFiles) return;
    return platform.onSharedFiles((files) => {
      const valid = files.filter((f) => f.uri.trim().length > 0);
      if (valid.length === 0) return;
      setPendingFiles(valid);
      navigate("/upload", { replace: true });
    });
  }, [platform, navigate]);

  /**
   * Persists peer selection across refreshes.
   */
  const handlePeerSelect = (deviceId: string) => {
    localStorage.setItem("lastSelectedPeer", deviceId);
    setSelectedPeer(deviceId);
  };

  /**
   * Opens the native document picker and uses the share send path.
   */
  const chooseFiles = async () => {
    setPicking(true);
    setMessage("");
    setCorrelationId(null);
    try {
      const result = await Beamlink.pickFiles();
      const valid = (result.files ?? []).filter((f) => f.uri.trim().length > 0);
      if (valid.length > 0) {
        setPendingFiles(valid);
      }
    } catch (err: unknown) {
      const problem = problemFromUnknown(err);
      setMessage(
        problem.detail ??
          (err instanceof Error ? err.message : "Picker failed"),
      );
      setCorrelationId(problem.correlationId);
    } finally {
      setPicking(false);
    }
  };

  /**
   * Starts native send with the same SharedFileItem refs as share.
   */
  const dispatch = async () => {
    if (waitingApproval) {
      setMessage("Waiting for approval.");
      return;
    }
    if (!selectedPeer) {
      setMessage("Select a destination peer first.");
      return;
    }
    if (isOffline) {
      setMessage("Peer is offline or has receive turned off");
      return;
    }
    if (pendingFiles.length === 0) {
      setMessage("No files chosen.");
      return;
    }
    const peer = selectedPeerObject!;
    setIsSending(true);
    setMessage("");
    setCorrelationId(null);
    const first = pendingFiles[0]?.name || "Send";
    const name =
      pendingFiles.length > 1
        ? `${first} +${pendingFiles.length - 1}`
        : first;
    rememberSendMeta(name, peer.deviceName);
    try {
      await Beamlink.sendFiles({
        files: pendingFiles,
        targetDeviceId: peer.id,
        targetIp: peer.ipAddress,
        targetPort: peer.port,
        tlsPort: peer.tlsPort,
        tlsFingerprint: peer.tlsFingerprint,
      });
      onTransferStarted();
    } catch (err: unknown) {
      setIsSending(false);
      const problem = problemFromUnknown(err);
      setMessage(
        problem.detail ??
          (err instanceof Error ? err.message : "Send failed"),
      );
      setCorrelationId(problem.correlationId);
    }
  };

  const peerOptions: PeerOption[] = peers.map((p) => ({
    deviceId: p.id,
    deviceName: p.deviceName,
    ipAddress: p.ipAddress,
    port: p.port,
    online: p.online,
  }));

  const peersProblem = peersQuery.error
    ? problemFromUnknown(peersQuery.error)
    : null;
  const peersError = peersQuery.error
    ? (peersProblem?.detail ??
      (peersQuery.error instanceof Error
        ? peersQuery.error.message
        : "Peer list failed"))
    : null;
  const peersCorrelationId = peersProblem?.correlationId ?? null;

  return (
    <Stack gap="lg" w="100%">
      {waitingApproval ? (
        <Alert color="yellow" variant="light">
          Waiting for approval. Register in Settings before sending.
        </Alert>
      ) : null}

      <Stack gap="sm">
        <PeerSelect
          options={peerOptions}
          value={selectedPeer}
          onChange={handlePeerSelect}
          disabled={isSending || waitingApproval}
          isOffline={isOffline}
        />
        <Button
          variant="default"
          disabled={waitingApproval || peersQuery.isFetching}
          loading={peersQuery.isFetching}
          onClick={() => void peersQuery.refetch()}
        >
          Refresh peers
        </Button>
      </Stack>

      {peersError ? (
        <Alert color="danger" variant="light">
          <ErrorWithRef message={peersError} correlationId={peersCorrelationId} />
        </Alert>
      ) : null}
      {!waitingApproval &&
      !peersError &&
      !peersQuery.isFetching &&
      peers.length === 0 ? (
        <Alert color="yellow" variant="light">
          No peers returned. Approve other devices or refresh.
        </Alert>
      ) : null}

      <Stack gap="sm">
        {pendingFiles.length === 0 ? (
          <Text size="sm" c="var(--bl-text-secondary)">
            No files chosen.
          </Text>
        ) : (
          <>
            <Text size="sm" c="var(--bl-text-secondary)">
              Files ({pendingFiles.length})
            </Text>
            <List size="sm">
              {pendingFiles.map((file, index) => (
                <List.Item key={`${file.uri}-${index}`}>
                  {file.name || file.uri}
                </List.Item>
              ))}
            </List>
          </>
        )}
        <Button
          fullWidth
          loading={picking}
          disabled={isSending || waitingApproval}
          onClick={() => void chooseFiles()}
        >
          Choose files
        </Button>
      </Stack>

      {pendingFiles.length > 0 ? (
        <Stack gap="sm">
          <Button
            fullWidth
            loading={isSending}
            disabled={!selectedPeer || isOffline || waitingApproval}
            onClick={() => void dispatch()}
          >
            Send
          </Button>
          {isSending ? (
            <Button
              variant="default"
              onClick={() => void Beamlink.cancelSend()}
            >
              Cancel
            </Button>
          ) : null}
        </Stack>
      ) : null}

      {message ? (
        <UploadBanner message={message} correlationId={correlationId} />
      ) : null}
    </Stack>
  );
};
