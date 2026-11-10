import { useState, useRef } from "react";
import { Stack, Title } from "@mantine/core";
import { usePlatform } from "@beamlink/platform";
import {
  BigDropDialog,
  ConnectBanner,
  DropZone,
  FileBrowserModal,
  PeerSelect,
  UploadBanner,
  dispatchErrorMessage,
  dispatchNotice,
  getDropStatus,
  shouldOpenTransfers,
  type PeerOption,
  type UploadBannerKind,
  useConnectOpen,
  useDropConfirm,
  usePeersQuery,
} from "@beamlink/features";
import { problemFromUnknown } from "@beamlink/core";
import { transferApi, peersApi } from "../api/transferApi";
import { useDeviceStatusStore } from "../store/deviceStatusStore";

interface FileUploadProps {
  onTransferStarted: () => void;
}

export const FileUpload = ({ onTransferStarted }: FileUploadProps) => {
  const platform = usePlatform();
  const deviceStatus = useDeviceStatusStore((s) => s.deviceStatus);
  const openConnect = useConnectOpen();
  const { data: peers = [] } = usePeersQuery(peersApi, {
    enabled: true,
  });

  const [showBrowser, setShowBrowser] = useState(false);
  const [selectedPeer, setSelectedPeer] = useState(
    () => localStorage.getItem("lastSelectedPeer") ?? "",
  );
  const [message, setMessage] = useState("");
  const [messageKind, setMessageKind] = useState<UploadBannerKind>("error");
  const [correlationId, setCorrelationId] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const dragCounter = useRef(0);

  const selectedPeerObject = peers.find((p) => p.id === selectedPeer);
  const isOffline = !!selectedPeerObject && !selectedPeerObject.online;

  /**
   * Persists peer selection across refreshes.
   * Restored on mount only if that peer is still in the list.
   */
  const handlePeerSelect = (deviceId: string) => {
    localStorage.setItem("lastSelectedPeer", deviceId);
    setSelectedPeer(deviceId);
  };

  const dispatch = async (paths: string[]) => {
    if (!selectedPeer) {
      setCorrelationId(null);
      setMessage("Select a destination peer first.");
      return;
    }
    if (isOffline) {
      setCorrelationId(null);
      setMessage("Peer is offline or has receive turned off");
      return;
    }
    const validPaths = paths.filter(Boolean);
    if (validPaths.length === 0) {
      setMessageKind("error");
      setCorrelationId(null);
      setMessage("No valid paths selected.");
      return;
    }

    const peer = selectedPeerObject!;
    setIsSending(true);
    setMessage("");
    setCorrelationId(null);

    try {
      const results = await transferApi.initiateDispatch({
        paths: validPaths,
        targetDeviceId: peer.id,
        targetIp: peer.ipAddress,
        targetPort: peer.tlsPort ?? peer.port,
      });
      const notice = dispatchNotice(results.items.length, results.skippedCount);
      if (notice) {
        setMessageKind(results.skippedCount > 0 ? "notice" : "error");
        setMessage(notice);
      }
      if (shouldOpenTransfers(results.items.length, results.skippedCount)) {
        onTransferStarted();
      }
    } catch (err: unknown) {
      setMessageKind("error");
      setMessage(dispatchErrorMessage(err));
      setCorrelationId(problemFromUnknown(err).correlationId);
    } finally {
      setIsSending(false);
    }
  };

  /** dragCounter prevents flicker when dragging over child elements */
  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current++;
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current--;
    if (dragCounter.current === 0) setIsDragging(false);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  const dropConfirm = useDropConfirm({
    getPathForFiles: (files) => platform.getPathForFiles(files),
    onAccept: async (paths) => {
      if (paths.length > 0) {
        await dispatch(paths);
      }
    },
    peerName: selectedPeerObject?.deviceName ?? "",
  });

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current = 0;
    setIsDragging(false);
    await dropConfirm.onDrop(e.dataTransfer);
  };

  const status = getDropStatus({
    isSending,
    hasPeer: !!selectedPeer,
    isOffline,
    isDragging,
  });
  const peerOptions: PeerOption[] = peers.map((p) => ({
    deviceId: p.id,
    deviceName: p.deviceName,
    ipAddress: p.ipAddress,
    port: p.port,
    tlsPort: p.tlsPort,
    online: p.online,
  }));

  return (
    <Stack gap="lg" w="100%" maw={900} mx="auto">
      <Title order={2} c="var(--bl-text-primary)">
        Send to Network
      </Title>

      <ConnectBanner state={deviceStatus?.state} onOpen={openConnect} />

      <PeerSelect
        options={peerOptions}
        value={selectedPeer}
        onChange={handlePeerSelect}
        disabled={isSending}
        isOffline={isOffline}
      />

      <DropZone
        status={status}
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        onChooseFiles={() => setShowBrowser(true)}
      />

      {message ? (
        <UploadBanner
          message={message}
          kind={messageKind}
          correlationId={correlationId}
        />
      ) : null}

      <FileBrowserModal
        isOpen={showBrowser}
        onClose={() => setShowBrowser(false)}
        onConfirm={async (paths) => {
          setShowBrowser(false);
          await dispatch(paths);
        }}
      />

      <BigDropDialog
        open={dropConfirm.open}
        body={dropConfirm.body}
        onConfirm={dropConfirm.confirmPendingDrop}
        onCancel={dropConfirm.cancelPendingDrop}
      />
    </Stack>
  );
};
