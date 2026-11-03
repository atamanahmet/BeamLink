package com.atamanahmet.beamlink.nexus.http;

import com.atamanahmet.beamlink.nexus.constants.TransferConstants;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.nexus.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.nexus.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import com.atamanahmet.beamlink.nexus.security.PeerTransport;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.RegisterRejectRule;
import com.atamanahmet.beamlink.nexus.service.SessionLostRule;
import com.atamanahmet.beamlink.nexus.service.StartOffsetRule;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
    private final DeviceService deviceService;

    /**
     * scheme://ip:port from stored device TLS fields.
     */
    public String baseUrl(String ip, int httpPort) {
        return transport(ip, httpPort).baseUrl(ip);
    }

    /**
     * Listed pin. Missing device or pin throws MissingPinException.
     */
    public String pin(String ip, int httpPort) {
        return transport(ip, httpPort).pin();
    }

    /**
     * Sends with the listed pin client. Missing pin is refused.
     */
    public HttpResponse<String> exchange(HttpRequest request, String ip, int httpPort)
            throws IOException, InterruptedException {
        return httpSender.sendPinned(request, pin(ip, httpPort));
    }

    private PeerTransport transport(String ip, int httpPort) {
        Device device = deviceService.findByLanAddress(ip, httpPort).orElse(null);
        if (device == null) {
            throw new PeerTransport.MissingPinException();
        }
        return PeerTransport.of(device.getTlsFingerprint(), device.getTlsPort(), device.getPort());
    }

    /**
     * Registers the transfer on the target before any chunks are sent.
     */
    public void registerTransfer(
            InitiateTransferRequest request,
            UUID transferId,
            String fileName,
            long fileSize
    ) {
        String url = baseUrl(request.getTargetIp(), request.getTargetPort())
                + "/api/transfers/receive";
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("transferId", transferId.toString());
            payload.put("sourceDeviceId", nexusService.getNexusId().toString());
            payload.put("fileName", fileName);
            payload.put("sourceIp", nexusService.getNexusIp());
            payload.put("sourcePort", nexusService.getNexusPort());
            payload.put("fileSize", fileSize);
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Public-Token",   nexusService.getPeerPublicToken())
                    .header("X-Public-Id",      nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.REQUEST_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = exchange(
                    httpRequest, request.getTargetIp(), request.getTargetPort());
            throwIfRegisterRejected(
                    response, "Target rejected transfer registration. Status: ");
        } catch (FileTransferException | TransferProtocolException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new FileTransferException(
                    "Timed out registering transfer with target: " + url,
                    e,
                    request.getTargetIp());
        } catch (Exception e) {
            throw new FileTransferException(
                    "Cannot reach target peer: " + url, e, request.getTargetIp());
        }
    }

    /**
     * Registers a batch transfer on the target before child sends start.
     */
    public void registerBatch(
            String targetIp,
            int targetPort,
            UUID batchTransferId,
            UUID sourceDeviceId,
            UUID dispatchId,
            int totalFiles,
            long totalSize,
            List<ReceiveBatchRequest.FileEntry> fileEntries
    ) {
        String url = baseUrl(targetIp, targetPort) + "/api/transfers/receive-batch";
        try {
            ReceiveBatchRequest payload = new ReceiveBatchRequest();
            payload.setBatchTransferId(batchTransferId);
            payload.setSourceDeviceId(sourceDeviceId);
            payload.setDispatchId(dispatchId);
            payload.setTotalFiles(totalFiles);
            payload.setTotalSize(totalSize);
            payload.setFiles(fileEntries);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TransferConstants.REQUEST_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = exchange(httpRequest, targetIp, targetPort);
            throwIfRegisterRejected(
                    response, "Target rejected batch registration. Status: ");

        } catch (FileTransferException | TransferProtocolException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new FileTransferException(
                    "Timed out registering batch with target: " + url, e, targetIp);
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target peer: " + url, e, targetIp);
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
            UUID sourceDeviceId,
            String directoryName,
            int totalFiles,
            long totalSize,
            List<String> emptyDirectories,
            List<ReceiveDirectoryRequest.FileEntry> fileEntries
    ) {
        String url = baseUrl(targetIp, targetPort)
                + "/api/transfers/receive-directory";
        try {
            ReceiveDirectoryRequest payload = new ReceiveDirectoryRequest();
            payload.setDirectoryTransferId(directoryTransferId);
            payload.setDispatchId(dispatchId);
            payload.setSourceDeviceId(sourceDeviceId);
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

            HttpResponse<String> response = exchange(httpRequest, targetIp, targetPort);
            throwIfRegisterRejected(
                    response, "Target rejected directory registration. Status: ");
        } catch (FileTransferException | TransferProtocolException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileTransferException("Cannot reach target peer", e, targetIp);
        } catch (IOException | JacksonException e) {
            throw new FileTransferException("Cannot reach target peer", e, targetIp);
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
        String url = baseUrl(targetIp, targetPort) + "/api/transfers/" + transferId + "/offset";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.REQUEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = exchange(request, targetIp, targetPort);
            if (response.statusCode() != 200) {
                if (SessionLostRule.isLost(jsonField(response, "code"))) {
                    throw new TransferProtocolException(
                            SessionLostRule.CODE,
                            SessionLostRule.MESSAGE,
                            HttpStatus.NOT_FOUND);
                }
                throw new FileTransferException(
                        statusErrorMessage(
                                "Target returned ",
                                response) + " when querying offset",
                        null);
            }

            Map<String, Long> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            return body.get("confirmedOffset");

        } catch (FileTransferException | TransferProtocolException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new FileTransferException(
                    "Timed out querying confirmed offset: " + url, e, targetIp);
        } catch (Exception e) {
            throw new FileTransferException("Cannot reach target to query offset: " + url, e, targetIp);
        }
    }

    /**
     * Throws protocol or file-transfer errors when register is not 200.
     */
    private void throwIfRegisterRejected(HttpResponse<String> response, String prefix) {
        if (response.statusCode() == 200) {
            return;
        }
        String code = jsonField(response, "code");
        String jsonDetail = jsonField(response, "detail");
        String fallback = statusErrorMessage(prefix, response);
        if (RegisterRejectRule.keepFailed(code)) {
            throw new TransferProtocolException(
                    code,
                    RegisterRejectRule.visibleMessage(jsonDetail, fallback),
                    HttpStatus.CONFLICT);
        }
        throw new FileTransferException(fallback, null);
    }

    /**
     * Reads one JSON string field from an HTTP body, or null. Bad JSON is ignored.
     */
    private String jsonField(HttpResponse<String> response, String field) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            Object value = map.get(field);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds status error text. Adds code, reason, and detail from JSON. Bad JSON uses status only.
     */
    private String statusErrorMessage(String prefix, HttpResponse<String> response) {
        String fallback = prefix + response.statusCode();
        String body = response.body();
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            Object code = map.get("code");
            Object reason = map.get("reason");
            Object detail = map.get("detail");
            if (code == null && reason == null && detail == null) {
                return fallback;
            }
            StringBuilder sb = new StringBuilder(fallback);
            if (code != null) {
                sb.append(" [").append(code).append(']');
            }
            if (reason != null) {
                sb.append(" [").append(reason).append(']');
            }
            if (detail != null) {
                sb.append(' ').append(detail);
            }
            return sb.toString();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Tells peer to cancel their side. Best effort, logs and continues on failure.
     */
    public void notifyCancelToPeer(String peerIp, int peerPort, UUID transferId) {
        String url = baseUrl(peerIp, peerPort) + "/api/transfers/" + transferId + "/cancel-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token",   nexusService.getPeerPublicToken())
                    .header("X-Public-Id",      nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .DELETE()
                    .build();

            HttpResponse<String> response = exchange(request, peerIp, peerPort);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on cancel notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Cancel notify failed for {}", transferId, e);
        }
    }

    /**
     * Tells peer to mark their side FAILED with the same reason. Best effort.
     */
    public void notifyFailToPeer(String peerIp, int peerPort, UUID transferId, String reason) {
        String url = baseUrl(peerIp, peerPort) + "/api/transfers/" + transferId + "/fail-notify";
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "reason", reason != null ? reason : "FAILED"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = exchange(request, peerIp, peerPort);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on fail notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Fail notify failed for {}", transferId, e);
        }
    }

    /**
     * Tells peer to pause their side. Best effort, logs and continues on failure.
     */
    public void notifyPauseToPeer(
            String peerIp, int peerPort, UUID transferId, PauseOwner pausedBy) {
        notifyPauseToPeer(peerIp, peerPort, transferId, pausedBy, null);
    }

    /**
     * Tells peer to pause their side. Best effort, logs and continues on failure.
     */
    public void notifyPauseToPeer(
            String peerIp, int peerPort, UUID transferId, PauseOwner pausedBy, String reason) {
        String url = baseUrl(peerIp, peerPort)
                + "/api/transfers/" + transferId + "/pause-notify";
        try {
            Map<String, String> payload = new java.util.HashMap<>();
            payload.put("pausedBy", pausedBy.name());
            if (reason != null && !reason.isBlank()) {
                payload.put("reason", reason);
            }
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = exchange(request, peerIp, peerPort);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on pause notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Pause notify failed for {}", transferId, e);
        }
    }

    /**
     * Tells peer to resume their side. Best effort, logs and continues on failure.
     */
    public void notifyResumeToPeer(String peerIp, int peerPort, UUID transferId) {
        String url = baseUrl(peerIp, peerPort)
                + "/api/transfers/" + transferId + "/resume-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = exchange(request, peerIp, peerPort);
            if (response.statusCode() != 200) {
                log.warn("Peer returned {} on resume notify for {}",
                        response.statusCode(), transferId);
            }
        } catch (Exception e) {
            log.warn("Resume notify failed for {}", transferId, e);
        }
    }

    /**
     * Asks peer to resume and throws PEER_UNREACHABLE when the peer does not accept.
     */
    public void requestResumeFromPeer(String peerIp, int peerPort, UUID transferId) {
        String url = baseUrl(peerIp, peerPort)
                + "/api/transfers/" + transferId + "/resume-notify";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Public-Token", nexusService.getPeerPublicToken())
                    .header("X-Public-Id", nexusService.getNexusId().toString())
                    .timeout(Duration.ofSeconds(TransferConstants.NOTIFY_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = exchange(request, peerIp, peerPort);
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
                    HttpStatus.SERVICE_UNAVAILABLE,
                    e);
        }
    }
}
