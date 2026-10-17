package com.atamanahmet.beamlink.nexus.http;

import com.atamanahmet.beamlink.nexus.constants.TransferConstants;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.nexus.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.StartOffsetRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferHttpClient {

    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;
    private final NexusService nexusService;

    /**
     * Registers the transfer on the target before any chunks are sent.
     */
    public void registerTransfer(InitiateTransferRequest request, UUID transferId, String fileName, long fileSize) {
        String url = "http://" + request.getTargetIp() + ":" + request.getTargetPort()
                + "/api/transfers/receive";
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "transferId",       transferId.toString(),
                    "sourceAgentId",    nexusService.getNexusId().toString(),
                    "fileName",         fileName,
                    "sourceIp",         nexusService.getNexusIp(),
                    "sourcePort",       nexusService.getNexusPort(),
                    "fileSize",         fileSize
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Public-Token",   nexusService.getPeerPublicToken())
                    .header("X-Public-Id",      nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.REQUEST_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);
            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected transfer registration. Status: " + response.statusCode(), null);
            }
        } catch (FileTransferException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new FileTransferException(
                    "Timed out registering transfer with target: " + url, e);
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target agent: " + url, e);
        }
    }

    /**
     * Registers a directory transfer on the target before child sends start.
     */
    public void registerDirectory(
            String targetIp,
            int targetPort,
            UUID directoryTransferId,
            UUID dispatchId,
            UUID sourceAgentId,
            String directoryName,
            int totalFiles,
            long totalSize,
            List<String> emptyDirectories,
            List<ReceiveDirectoryRequest.FileEntry> fileEntries
    ) {
        String url = "http://" + targetIp + ":" + targetPort
                + "/api/transfers/receive-directory";
        try {
            ReceiveDirectoryRequest payload = new ReceiveDirectoryRequest();
            payload.setDirectoryTransferId(directoryTransferId);
            payload.setDispatchId(dispatchId);
            payload.setSourceAgentId(sourceAgentId);
            payload.setDirectoryName(directoryName);
            payload.setTotalFiles(totalFiles);
            payload.setTotalSize(totalSize);
            payload.setEmptyDirectories(emptyDirectories);
            payload.setFiles(fileEntries);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TransferConstants.REGISTER_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);
            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected directory registration. Status: "
                                + response.statusCode(), null);
            }
        } catch (FileTransferException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileTransferException("Cannot reach target agent", e);
        } catch (IOException e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    /**
     * Start offset from receiver /offset when local row has progress, else 0.
     */
    public long resolveStartOffset(
            String targetIp, Integer targetPort, UUID transferId, long localConfirmedOffset) {
        if (!StartOffsetRule.needsReceiverQuery(localConfirmedOffset)) {
            return 0L;
        }
        if (targetIp == null || targetIp.isBlank() || targetPort == null) {
            throw new FileTransferException(
                    "Cannot query offset: missing peer address", null);
        }
        return queryConfirmedOffset(targetIp, targetPort, transferId);
    }

    /**
     * Queries the target's confirmed offset before resuming a paused transfer.
     */
    public long queryConfirmedOffset(String targetIp, int targetPort, UUID transferId) {
        String url = "http://" + targetIp + ":" + targetPort + "/api/transfers/" + transferId + "/offset";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target returned " + response.statusCode() + " when querying offset", null);
            }

            Map<String, Long> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            return body.get("confirmedOffset");

        } catch (FileTransferException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new FileTransferException(
                    "Timed out querying confirmed offset: " + url, e);
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target to query offset: " + url, e);
        }
    }

    /**
     * Tells peer to cancel their side. Best effort, logs and continues on failure.
     */
    public void notifyCancelToPeer(String peerIp, int peerPort, UUID transferId) {
        String url = "http://" + peerIp + ":" + peerPort + "/api/transfers/" + transferId + "/cancel-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token",   nexusService.getPeerPublicToken())
                    .header("X-Public-Id",      nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on cancel notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Cancel notify failed for {}: {}", transferId, e.getMessage());
        }
    }

    /**
     * Tells peer to pause their side. Best effort, logs and continues on failure.
     */
    public void notifyPauseToPeer(
            String peerIp, int peerPort, UUID transferId, PauseOwner pausedBy) {
        String url = "http://" + peerIp + ":" + peerPort
                + "/api/transfers/" + transferId + "/pause-notify";
        try {
            String body = objectMapper.writeValueAsString(Map.of("pausedBy", pausedBy.name()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on pause notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Pause notify failed for {}: {}", transferId, e.getMessage());
        }
    }

    /**
     * Tells peer to resume their side. Best effort, logs and continues on failure.
     */
    public void notifyResumeToPeer(String peerIp, int peerPort, UUID transferId) {
        String url = "http://" + peerIp + ":" + peerPort
                + "/api/transfers/" + transferId + "/resume-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on resume notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Resume notify failed for {}: {}", transferId, e.getMessage());
        }
    }

    /**
     * Asks peer to resume and throws PEER_UNREACHABLE when the peer does not accept.
     */
    public void requestResumeFromPeer(String peerIp, int peerPort, UUID transferId) {
        String url = "http://" + peerIp + ":" + peerPort
                + "/api/transfers/" + transferId + "/resume-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() != 200) {
                throw new TransferProtocolException(
                        "PEER_UNREACHABLE",
                        "Peer did not accept resume",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
        } catch (TransferProtocolException e) {
            throw e;
        } catch (Exception e) {
            throw new TransferProtocolException(
                    "PEER_UNREACHABLE",
                    "Cannot reach peer to resume",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
