import { useState, useRef } from "react";
import { Alert, Stack, Title } from "@mantine/core";
import axios from "axios";
import { usePlatform } from "@beamlink/platform";
import {
  DropZone,
  FileBrowserModal,
  PeerSelect,
  getDropStatus,
  type PeerOption,
  usePeersQuery,
} from "@beamlink/features";
import { transferApi, peersApi } from "../api/transferApi";
import { useAuthStore } from "../store/authStore";

interface FileUploadProps {
  onTransferStarted: () => void;
}

/**
 * Reads a dispatch error message from axios or Error.
 */
function dispatchErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data;
    if (data && typeof data === "object" && "message" in data) {
      const message = data.message;
      if (typeof message === "string" && message.length > 0) return message;
    }
    if (err.message) return err.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return "Dispatch failed.";
}

export const FileUpload = ({ onTransferStarted }: FileUploadProps) => {
  const platform = usePlatform();
  const isAuthenticated = useAuthStore((s) => s.status === "authenticated");
  const { data: peers = [] } = usePeersQuery(peersApi, {
    enabled: isAuthenticated,
  });

  const [showBrowser, setShowBrowser] = useState(false);
  const [selectedPeer, setSelectedPeer] = useState(
    () => localStorage.getItem("lastSelectedPeer") ?? "",
  );
  const [message, setMessage] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const dragCounter = useRef(0);

  const selectedPeerObject = peers.find((p) => p.id === selectedPeer);
  const isOffline = !!selectedPeerObject && !selectedPeerObject.online;

  const handlePeerSelect = (id: string) => {
    localStorage.setItem("lastSelectedPeer", id);
    setSelectedPeer(id);
  };

  const dispatch = async (paths: string[]) => {
    if (!selectedPeer) {
      setMessage("Select a destination agent first.");
      return;
    }
    if (isOffline) {
      setMessage("Selected agent is offline.");
      return;
    }
    const validPaths = paths.filter(Boolean);
    if (validPaths.length === 0) {
      setMessage("No valid paths selected.");
      return;
    }

    const peer = selectedPeerObject!;
    setIsSending(true);
    setMessage("");

    try {
      const results = await transferApi.initiateDispatch({
        paths: validPaths,
        targetAgentId: peer.id,
        targetIp: peer.ipAddress,
        targetPort: peer.port,
      });

      if (results.length > 0) {
        onTransferStarted();
      } else {
        setMessage("Nothing was initiated. Check the selected paths.");
      }
    } catch (err: unknown) {
      setMessage(`Failed: ${dispatchErrorMessage(err)}`);
    } finally {
      setIsSending(false);
    }
  };

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

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current = 0;
    setIsDragging(false);

    const files = Array.from(e.dataTransfer.files);
    if (files.length === 0) return;

    const paths = await platform.getPathForFiles(files);
    if (paths.length > 0) {
      await dispatch(paths);
    } else {
      setMessage("Could not read file paths. Try using Choose Files instead.");
    }
  };

  const status = getDropStatus({
    isSending,
    hasPeer: !!selectedPeer,
    isOffline,
    isDragging,
  });
  const peerOptions: PeerOption[] = peers.map((p) => ({
    agentId: p.id,
    agentName: p.agentName,
    ipAddress: p.ipAddress,
    port: p.port,
    online: p.online,
  }));

  return (
    <Stack gap="lg" w="100%" maw={900} mx="auto">
      <Title order={2} c="var(--bl-text-primary)">
        Send to Network
      </Title>

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
        <Alert color="danger" variant="light">
          {message}
        </Alert>
      ) : null}

      <FileBrowserModal
        isOpen={showBrowser}
        onClose={() => setShowBrowser(false)}
        onConfirm={async (paths) => {
          setShowBrowser(false);
          await dispatch(paths);
        }}
      />
    </Stack>
  );
};
